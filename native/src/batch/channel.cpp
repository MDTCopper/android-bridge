#include "batch/internal/channel.h"

#include "gen/batch.h"
#include "util/log.h"

#include <cassert>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>

// The channels themselves, and the ring each one is.
//
// Which channels exist and what they may hold is the generated table's, so nothing here names a channel.
// What is this file's is the mechanism - one buffer per channel, two positions that never meet, and the
// rule that a channel which cannot take a frame drops the newest one and says so.
//
// The ring's one invariant: a frame is a four byte length followed by that many payload bytes, written
// entirely inside the buffer, and the gap between two frames - the tail a frame did not fit in - is always
// readable as a marker. A marker is four bytes when the tail has four, and only the tail's own bytes when it
// has fewer, so it never reaches past `capacity`; the producer advances the write position over the whole
// tail and the reader steps over the whole tail, which is the same span for both. Between two positions the
// reader therefore finds only whole frames and markers, and no byte of the buffer is ever addressed past
// `capacity`.

namespace copper::bridge::batch {
    namespace Batch = gen::Batch;

    namespace {

    using Batch::BATCH_COUNT;
    using Batch::BATCH_ENTRIES;

    /** The length prefix of a padding element: it fills the tail so no frame straddles the end. */
    constexpr uint32_t JUMP = 0xFFFFFFFFu;

    /** The capacity a channel starts with; it doubles as frames get larger, up to the declared bound. */
    constexpr size_t INITIAL_CAPACITY = 4096;

    void WriteU32(uint8_t* data, size_t at, uint32_t value) {
        memcpy(data + at, &value, sizeof(value));
    }

    uint32_t ReadU32(const uint8_t* data, size_t at) {
        uint32_t value = 0;
        memcpy(&value, data + at, sizeof(value));
        return value;
    }

    /**
     * How much of the tail a marker takes when the tail is too short for the whole four byte one: the last
     * byte of the marker is left in the next lap's first index, which is where the frame after the wrap is
     * written anyway. So the marker never reaches past the buffer.
     */
    constexpr size_t MARKER_BYTES = sizeof(uint32_t);

    /** The bytes the tail at `offset` takes of a marker: a whole one, or only what the tail has. */
    size_t MarkerBytes(size_t offset, size_t capacity) {
        const size_t tail = capacity - offset;
        return tail < MARKER_BYTES ? tail : MARKER_BYTES;
    }

    /**
     * Writes such a marker over the tail: the whole four byte word when the tail has it, and exactly the
     * tail's own bytes when it does not. It never writes a byte at or past `capacity`, which is what lets
     * the producer cover a tail of one to three bytes - a tail too short for a whole marker - without
     * touching what follows the buffer.
     */
    void WriteMarker(uint8_t* buffer, size_t offset, size_t capacity) {
        const size_t bytes = MarkerBytes(offset, capacity);
        assert(offset + bytes <= capacity && "a marker never reaches past the ring");
        const uint8_t marker[MARKER_BYTES] = {0xFF, 0xFF, 0xFF, 0xFF};
        memcpy(buffer + offset, marker, bytes);
    }

    /** One element of the ring, as a reader finds it while walking from `at` towards `write`. */
    struct Element {
        /** The length prefix, when the element is a frame. */
        uint32_t length = 0;

        /** The bytes from `at` to the next element: the whole frame, or the tail a marker covers. */
        size_t step = 0;

        /** The frame's payload, or null when this element is a marker. */
        const uint8_t* data = nullptr;
    };

    /**
     * The element at `at`, which must be behind `write`.
     *
     * A frame is a four byte length and that many payload bytes, all of them inside the buffer. Anything
     * else in the ring is the tail a frame did not fit in.
     *
     * Tails of four bytes or more are covered by the whole marker, so they are read as they stand. A
     * shorter tail gets only the marker's own bytes, and the rest of the four byte word a reader loads
     * comes from the next lap's first bytes - which is why what is there decides: the producer does not
     * write a frame's length there without writing its first payload byte over that same word first. So
     * the value can only be the marker, and the frame after it is the next element.
     */
    Element Next(const uint8_t* buffer, size_t capacity, size_t at) {
        const size_t offset = at % capacity;
        const size_t tail = capacity - offset;
        if (tail < MARKER_BYTES)
            return Element{0, tail, nullptr};

        const uint32_t value = ReadU32(buffer, offset);
        if (value == JUMP)
            return Element{0, tail, nullptr};

        return Element{value, MARKER_BYTES + value, buffer + offset + MARKER_BYTES};
    }

    // The throttle's own state, shared by every channel: one line a second out of this file.
    std::mutex logMutex;
    std::chrono::steady_clock::time_point lastLog;

    // Throttled, because a channel that drops every frame would otherwise turn its own report into the
    // high frequency work this queue exists to avoid.
    void LogThrottled(const char* format, ...) {
        std::lock_guard<std::mutex> guard(logMutex);
        const auto now = std::chrono::steady_clock::now();
        if (lastLog != std::chrono::steady_clock::time_point() && now - lastLog < std::chrono::seconds(1))
            return;
        lastLog = now;

        char buffer[256];
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, sizeof(buffer), format, args);
        va_end(args);
        util::Log::Warn("BATCH", buffer);
    }

    // The channels themselves, configured from the generated table when this library is loaded: nothing they
    // need exists later than a load, and a table built by the first frame would be built on whichever thread
    // happened to write it.
    struct ChannelTable {
        Channel entries[BATCH_COUNT];

        ChannelTable() {
            for (int i = 0; i < BATCH_COUNT; i++) {
                entries[i].name = BATCH_ENTRIES[i].name;
                entries[i].maxBatches = static_cast<size_t>(BATCH_ENTRIES[i].maxBatches);
                entries[i].maxBytes = static_cast<size_t>(BATCH_ENTRIES[i].maxBytes);
            }
        }
    };

    ChannelTable table;

    } // namespace

Channel* Channel::Of(int32_t channel) {
    if (channel < 0 || channel >= BATCH_COUNT)
        return nullptr;
    return &table.entries[channel];
}

void Channel::Push(const uint8_t* data, size_t length) {
    if (data == nullptr || length == 0)
        return;

#ifndef NDEBUG
    // The queue is single producer by construction; a second one would race the write position.
    if (producer == std::thread::id())
        producer = std::this_thread::get_id();
    else
        assert(producer == std::this_thread::get_id() && "a batch channel has one producer");
#endif

    const size_t total = length + 4;
    if (total > maxBytes) {
        Drop();
        return;
    }

    EnsureCapacity(total);
    if (buffer == nullptr || total > capacity) {
        Drop();
        return;
    }

    const size_t write = writePos.load(std::memory_order_relaxed);
    const size_t read = readPos.load(std::memory_order_acquire);
    const size_t offset = write % capacity;
    const size_t tail = capacity - offset;

    // A jump element covers the tail when the frame would not fit in one piece, so a reader never
    // has to follow a frame across the end of the buffer. It takes four bytes when the tail has them and
    // only the tail when it does not, which is still readable: see `Next`. `padding` is the whole tail, so
    // the marker - at most four bytes and at most `padding` - lies inside the buffer, and the write position
    // moves over the span the marker covers rather than over the bytes it wrote.
    const size_t padding = total > tail ? tail : 0;
    if (write - read + padding + total > capacity) {
        Drop();
        return;
    }
    if (batches.load(std::memory_order_relaxed) + 1 > maxBatches) {
        Drop();
        return;
    }

    if (padding > 0)
        WriteMarker(buffer, offset, capacity);

    // Wrapped, not past the end: the positions are absolute counters, so the padding takes the frame to the
    // ring's index zero. Writing it at `offset + padding` would be one whole lap beyond what was allocated.
    const size_t start = (offset + padding) % capacity;
    // The frame itself always fits: `padding` is the whole tail, so what is left after it - from index zero
    // when it wrapped, or the whole buffer when it did not - is at least `total` by the check above.
    WriteU32(buffer, start, static_cast<uint32_t>(length));
    memcpy(buffer + start + sizeof(uint32_t), data, length);

    writePos.store(write + padding + total, std::memory_order_release);
    batches.fetch_add(1, std::memory_order_release);
}

bool Channel::PeekFirst(const uint8_t*& data, size_t& length) {
    if (buffer == nullptr)
        return false;

    for (;;) {
        const size_t read = readPos.load(std::memory_order_relaxed);
        const size_t write = writePos.load(std::memory_order_acquire);
        if (read == write)
            return false;

        const Element element = Next(buffer, capacity, read);
        if (element.data == nullptr) {
            // A marker where a frame was expected: padding is not a frame, so step over the tail and look
            // again, which is where the frame the producer wrapped is.
            readPos.store(read + element.step, std::memory_order_release);
            continue;
        }

        length = element.length;
        data = element.data;
        return true;
    }
}

void Channel::ConsumeFirst() {
    if (buffer == nullptr)
        return;

    const size_t read = readPos.load(std::memory_order_relaxed);
    const Element element = Next(buffer, capacity, read);
    readPos.store(read + element.step, std::memory_order_release);
    batches.fetch_sub(1, std::memory_order_release);
}

size_t Channel::PendingBytes() const {
    if (buffer == nullptr)
        return 0;

    const size_t read = readPos.load(std::memory_order_acquire);
    const size_t write = writePos.load(std::memory_order_acquire);
    if (read == write)
        return 0;

    // Walk the queued frames rather than subtracting the positions: the difference would include
    // whatever padding the producer wrote, which the destination does not have to hold. The walk is
    // bounded by `write`, so a walk that cannot reach it is a bug rather than a queue - and saying so
    // beats spinning here forever, which is what a reader that could not follow the producer did.
    size_t total = 0;
    size_t at = read;
    while (at != write) {
        const Element element = Next(buffer, capacity, at);
        if (element.step == 0 || element.step > write - at)
            return 0;
        if (element.data != nullptr)
            total += element.step;
        at += element.step;
    }
    return total;
}

void Channel::Drop() {
    // The report carries the count itself: there is no way to ask a channel how many frames it has dropped.
    const long long total = dropped.fetch_add(1, std::memory_order_relaxed) + 1;
    LogThrottled("batch backlog full, dropped the newest batch (%s, %lld so far)", name, total);
}

void Channel::EnsureCapacity(size_t needed) {
    if (buffer != nullptr && capacity >= needed)
        return;

    size_t wanted = capacity == 0 ? INITIAL_CAPACITY : capacity;
    while (wanted < needed && wanted < maxBytes)
        wanted *= 2;
    if (wanted > maxBytes)
        wanted = maxBytes;
    if (wanted < needed)
        return;

    // Growing moves every position, so it is only done when the ring is empty; a frame that does not fit the
    // current capacity while other frames are queued is dropped instead.
    if (writePos.load(std::memory_order_relaxed) != readPos.load(std::memory_order_relaxed))
        return;

    delete[] buffer;
    buffer = new uint8_t[wanted]();
    capacity = wanted;
    writePos.store(0, std::memory_order_relaxed);
    readPos.store(0, std::memory_order_relaxed);
}

} // namespace copper::bridge::batch