#ifndef COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H
#define COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <thread>

// One queue: the ring behind a single batch channel, and the bounds the generated table gives it.
//
// A batch channel may be written every frame, so nothing here is locked - the producer owns the write
// position and the consumer the read one, and neither touches the other's, so the UI thread never waits for
// a lock the game thread holds - and nothing is unbounded: a full channel drops the newest frame and counts
// it, because the consumer cannot drop on the producer's behalf without corrupting a write in flight.
//
// A frame is stored as [u32 length][bytes] and never straddles the end of the buffer: the producer covers
// the tail with a jump element, four bytes of 0xff, or with the tail itself when the tail is shorter than
// four bytes - the marker then ends in the byte the next lap's first frame is written over, so no byte past
// the buffer is ever touched. A reader tells a frame from a marker by the length it reads: a frame's bytes
// all lie before the buffer's end, a marker's do not. The ring grows up to the channel's byte bound, and
// only while it is empty.

namespace copper::bridge::batch {

/** One channel: a single producer, single consumer queue with a fixed frame and byte bound. */
struct Channel {
    /** What the generated table declares, kept for the log line a full channel writes. */
    const char* name = nullptr;
    size_t maxBatches = 0;
    size_t maxBytes = 0;

    /** Queues one frame; drops the newest and counts it when the channel cannot take it. */
    void Push(const uint8_t* data, size_t length);

    /** The oldest frame, or false when the channel is empty. Valid until {@link ConsumeFirst}. */
    bool PeekFirst(const uint8_t*& data, size_t& length);

    /** Releases the frame {@link PeekFirst} returned. */
    void ConsumeFirst();

    /** How many bytes the queued frames take, length prefixes included. */
    size_t PendingBytes() const;

    /** The channel with this id, or nullptr when the id is not one of the generated ones. */
    static Channel* Of(int32_t channel);

private:
    /** Makes room for one frame, which is only possible while nothing is queued. */
    void EnsureCapacity(size_t needed);

    /** Counts one dropped frame and reports it, at most once a second. */
    void Drop();

    /** Allocated on first use and grown while empty, so a channel nobody writes costs nothing. */
    uint8_t* buffer = nullptr;
    size_t capacity = 0;

    /** Monotonic byte positions: the producer writes one, the consumer the other, never both. */
    std::atomic<size_t> writePos{0};
    std::atomic<size_t> readPos{0};
    std::atomic<size_t> batches{0};
    std::atomic<long long> dropped{0};

    /** The thread that first produced, checked in debug builds against a second one. */
    std::thread::id producer{};
};

} // namespace copper::bridge::batch

#endif // COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H
