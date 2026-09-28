#include "batch/internal/channel.h"

#include "gen/batch.h"
#include "util/log.h"

#include <cassert>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>

// The channels, and the ring each one is: one buffer per channel, two positions that never meet, and a channel that
// cannot take a frame dropping the newest one. The ring's invariant: a gap a frame did not fit in reads as a marker.

namespace copper::bridge::batch {
    namespace Batch = gen::Batch;

    namespace {

    using Batch::BATCH_COUNT;
    using Batch::BATCH_ENTRIES;

    /** The length prefix of a padding element: it fills the tail so no frame straddles the end. */
    /** How much of the tail a marker takes when the tail is shorter than the whole four byte one. */
    constexpr uint32_t JUMP = 0xFFFFFFFFu;

    constexpr size_t INITIAL_CAPACITY = 4096;

    void WriteU32(uint8_t* data, size_t at, uint32_t value) {
        memcpy(data + at, &value, sizeof(value));
    }

    uint32_t ReadU32(const uint8_t* data, size_t at) {
        uint32_t value = 0;
        memcpy(&value, data + at, sizeof(value));
        return value;
    }

    constexpr size_t MARKER_BYTES = sizeof(uint32_t);

    size_t MarkerBytes(size_t offset, size_t capacity) {
        const size_t tail = capacity - offset;
        return tail < MARKER_BYTES ? tail : MARKER_BYTES;
    }

    /** Writes the marker over the tail, cut where it does not fit. Never writes at or past `capacity`. */
    void WriteMarker(uint8_t* buffer, size_t offset, size_t capacity) {
        const size_t bytes = MarkerBytes(offset, capacity);
        assert(offset + bytes <= capacity && "a marker never reaches past the ring");
        const uint8_t marker[MARKER_BYTES] = {0xFF, 0xFF, 0xFF, 0xFF};
        memcpy(buffer + offset, marker, bytes);
    }

    struct Element {
        uint32_t length = 0;

        size_t step = 0;

        const uint8_t* data = nullptr;
    };

    /**
     * The element at `at`, which must be behind `write`. A frame is a four byte length and that many payload bytes,
     * all inside the buffer; anything else is the tail a frame did not fit in, and a shorter tail holds only the
     * marker, so the value a reader loads there can only be the marker.
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

    std::mutex logMutex;
    std::chrono::steady_clock::time_point lastLog;

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

    // Configured from the generated table at load; building it on the first frame would use that thread.
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

    // A jump element covers the tail when the frame would not fit in one piece. See `Next`.
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

    // Wrapped, not past the end: the padding takes the frame to the ring's index zero.
    const size_t start = (offset + padding) % capacity;
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
            // A marker where a frame was expected: step over the tail and look again.
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

    // Walk the queued frames: subtracting the positions would count the padding. An index past `write` is a bug.
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

    // Growing moves every position, so it happens only while the ring is empty; a frame that does not fit is dropped.
    if (writePos.load(std::memory_order_relaxed) != readPos.load(std::memory_order_relaxed))
        return;

    delete[] buffer;
    buffer = new uint8_t[wanted]();
    capacity = wanted;
    writePos.store(0, std::memory_order_relaxed);
    readPos.store(0, std::memory_order_relaxed);
}

} // namespace copper::bridge::batch