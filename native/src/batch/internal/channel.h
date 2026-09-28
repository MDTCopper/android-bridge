#ifndef COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H
#define COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <thread>

// One queue: the ring behind a single batch channel, and the bounds the generated table gives it. Nothing is locked -
// the producer owns the write position, the consumer the read one - and a full channel drops the newest frame.
// A frame is [u32 length][bytes] and never straddles the end of the buffer: the tail is covered by a jump element.

namespace copper::bridge::batch {

struct Channel {
    const char* name = nullptr;
    size_t maxBatches = 0;
    size_t maxBytes = 0;

    /** Queues one frame; drops the newest and counts it when the channel cannot take it. */
    /** The oldest frame, or false when the channel is empty. Valid until {@link ConsumeFirst}. */
    void Push(const uint8_t* data, size_t length);

    bool PeekFirst(const uint8_t*& data, size_t& length);

    void ConsumeFirst();

    size_t PendingBytes() const;

    static Channel* Of(int32_t channel);

private:
    void EnsureCapacity(size_t needed);

    void Drop();

    uint8_t* buffer = nullptr;
    size_t capacity = 0;

    /** Monotonic byte positions: the producer writes one, the consumer the other, never both. */
    std::atomic<size_t> writePos{0};
    std::atomic<size_t> readPos{0};
    std::atomic<size_t> batches{0};
    std::atomic<long long> dropped{0};

    std::thread::id producer{};
};

} // namespace copper::bridge::batch

#endif // COPPER_BRIDGE_BATCH_INTERNAL_CHANNEL_H
