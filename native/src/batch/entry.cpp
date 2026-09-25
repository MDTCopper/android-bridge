#include "batch/entry.h"

#include "batch/internal/channel.h"

#include <cstring>

namespace copper::bridge::batch::Entry {

    namespace {

        // Every number in the frame stream is a uint32: the total that follows the head, and each frame's own
        // length.
        constexpr size_t PREFIX = sizeof(uint32_t);

        bool Queue(int32_t channel, const uint8_t* data, size_t length) {
            Channel* target = Channel::Of(channel);
            if (target == nullptr || data == nullptr || length == 0)
                return false;

            target->Push(data, length);
            return true;
        }

        size_t FramedBytes(int32_t channel) {
            Channel* target = Channel::Of(channel);
            return target == nullptr ? PREFIX : PREFIX + target->PendingBytes();
        }

        /**
         * Moves the queued frames into `out`, whose first four bytes are the byte count of the frames that
         * followed, and returns how many of `out`'s bytes they took.
         *
         * `capacity` is how large the caller's buffer is, and it is the only bound every copy obeys. The
         * producer runs while this copies, so which frames fit is decided here, against the buffer itself,
         * rather than by a measurement taken before it: a frame that arrived since and does not fit is left
         * in the ring for the next call rather than copied past the end. The count is written last, because
         * until the loop ends it is not known.
         */
        size_t Drain(int32_t channel, uint8_t* out, size_t capacity) {
            if (out == nullptr || capacity < PREFIX)
                return 0;

            Channel* target = Channel::Of(channel);
            uint8_t* cursor = out + PREFIX;
            size_t written = PREFIX;

            if (target != nullptr) {
                const uint8_t* data = nullptr;
                size_t length = 0;
                while (target->PeekFirst(data, length)) {
                    // The count `written` makes is the frame plus its prefix, so the room left is
                    // `capacity - written` - subtracting the prefix a second time would understate it, and
                    // on a buffer of exactly the prefix's size it would wrap and let the copy through.
                    // `written` never exceeds `capacity`, so the copies below stay inside `out`.
                    if (PREFIX + length > capacity - written)
                        break;
                    const auto size = static_cast<uint32_t>(length);
                    memcpy(cursor, &size, PREFIX);
                    cursor += PREFIX;
                    memcpy(cursor, data, length);
                    cursor += length;
                    written += PREFIX + length;
                    // Consumed as it is copied: the frames are in the caller's hands now, and holding
                    // them in the ring as well would only make the producer drop the next ones.
                    target->ConsumeFirst();
                }
            }

            const auto total = static_cast<uint32_t>(written - PREFIX);
            memcpy(out, &total, PREFIX);
            return written;
        }

    } // namespace

    void Push(JNIEnv* env, jclass, jint channel, jbyteArray buffer, jint length) {
        if (buffer == nullptr || length <= 0)
            return;

        const jsize available = env->GetArrayLength(buffer);
        const jsize capped = length > available ? available : length;
        jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
        if (bytes == nullptr) {
            env->ExceptionClear();
            return;
        }

        // Read only, so the array is released without copying anything back.
        Queue(channel, reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(capped));
        env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT);
    }

    jbyteArray Poll(JNIEnv* env, jclass, jint channel, jbyteArray reuse) {
        const jsize needed = static_cast<jsize>(FramedBytes(channel));

        // The array is reused when it is already large enough; otherwise a right sized one is handed back,
        // and the caller keeps it for the next frame.
        jbyteArray destination = reuse;
        if (destination == nullptr || env->GetArrayLength(destination) < needed) {
            destination = env->NewByteArray(needed);
            if (destination == nullptr) {
                env->ExceptionClear();
                return nullptr;
            }
        }

        jbyte* out = env->GetByteArrayElements(destination, nullptr);
        if (out == nullptr) {
            env->ExceptionClear();
            return destination;
        }

        // The array's own length is what bounds the copy, not the measurement: the reused array may be
        // larger, and a frame that arrived after the measurement would make `needed` too small.
        Drain(channel, reinterpret_cast<uint8_t*>(out),
                static_cast<size_t>(env->GetArrayLength(destination)));
        env->ReleaseByteArrayElements(destination, out, 0);
        return destination;
    }

} // namespace copper::bridge::batch::Entry
