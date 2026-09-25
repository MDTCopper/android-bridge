#include "bus/waiters.h"
#include "bus/internal/waiters.h"

#include "jni/env.h"
#include "bus/mailbox.h"
#include "bus/internal/mailbox.h"
#include "util/log.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <mutex>
#include <unordered_map>
#include <utility>

// The waiters, and the two calls that are about waiting rather than about queueing.
//
// A synchronous row is queued like any other, but its caller stays on this thread until the answer
// arrives, so the request is registered before the message is queued and completed by whoever performs it.
// Ids come from this file rather than from the Java side's counter: the two never meet.

namespace copper::bridge::bus::Waiters {
    namespace Bus = gen::Bus;

    namespace {

        // One request waiting for its answer, registered under its request id before anything is queued.
        struct Waiter {
            std::mutex mutex;
            std::condition_variable condition;
            bool done = false;
            bool object = false;
            jvalue value{};
        };

        // The registry, the lock that keeps its two sides apart, and the ids its entries are filed under.
        // They exist with this library rather than with the first wait: a synchronous call may be made
        // before anything else in the bus has run.
        std::mutex waiterMutex;
        std::unordered_map<int64_t, Waiter*> waiters;
        // Counted from one: zero is what a message that asks for no answer carries.
        std::atomic<int64_t> nextWaiterId{1};

        int64_t NextWaiterId() {
            return nextWaiterId.fetch_add(1);
        }

        // Whether a side's pump has stopped for good: one flag per side, because a stop belongs to one pump.
        // Only ART is ever marked - Release stops it - while the JVM side's pump ends with the process.
        std::atomic<bool> artPumpStopped{false};
        std::atomic<bool> jvmPumpStopped{false};

        bool PumpStopped(jni::Side side) {
            return side == jni::Side::Art ? artPumpStopped.load() : jvmPumpStopped.load();
        }

        // The throttle's own state, shared by every thread that waits: one line a second out of this file.
        std::mutex logMutex;
        std::chrono::steady_clock::time_point lastLog;

        // Throttled: a caller that waits every frame would otherwise turn its own report into the high
        // frequency work the queue exists to avoid.
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
            util::Log::Warn("BUS", buffer);
        }

    } // namespace

    void EnqueueAndWait(jni::Side to, Message&& message, jvalue& answer) {
        answer = jvalue{};

        Waiter waiter;
        const int64_t id = NextWaiterId();
        const Bus::Kind kind = message.kind;
        message.requestId = id;

        // Registered before the message is queued: the answer may otherwise arrive first, find no waiter,
        // and be dropped. Queueing sits in this same critical section as the stop's flag and clear, so a
        // call that arrives as the pump stops is either wholly before it - cleared and released - or wholly
        // after it, where the flag drops it before anything is queued.
        bool stopped = false;
        {
            std::lock_guard<std::mutex> guard(waiterMutex);
            stopped = PumpStopped(to);
            if (!stopped) {
                waiters[id] = &waiter;
                Mailbox::Enqueue(to, std::move(message));
            }
        }

        if (stopped) {
            // The pump that would perform this call has stopped for good, so nothing will ever answer it:
            // the neutral value is the answer, and the payload goes back like any other discarded message.
            message.Dispose();
            return;
        }

        {
            std::unique_lock<std::mutex> lock(waiter.mutex);
            while (!waiter.done) {
                if (waiter.condition.wait_for(lock, std::chrono::seconds(5)) == std::cv_status::timeout
                        && !waiter.done) {
                    // Reported, then waited out: giving up would leave the game in an unknown state, and
                    // the handler answers within a frame in every case this bridge knows of.
                    LogThrottled("still waiting for %s (5s)", Bus::NameOf(kind));
                }
            }
        }

        {
            std::lock_guard<std::mutex> guard(waiterMutex);
            waiters.erase(id);
        }

        answer = waiter.value;
        if (waiter.object) {
            // The answer was handed over as a global reference, because a local one belongs to the thread
            // that made it. It becomes a local reference of the caller here.
            jni::Env callerEnv(jni::Env::Other(to));
            JNIEnv* env = callerEnv.Get();
            if (env != nullptr && answer.l != nullptr) {
                jobject local = env->NewLocalRef(answer.l);
                env->DeleteGlobalRef(answer.l);
                answer.l = local;
            }
        }
    }

    void Complete(int64_t id, const jvalue& value, bool object, JNIEnv* callerEnv) {
        if (id == 0)
            return;

        std::lock_guard<std::mutex> guard(waiterMutex);
        auto found = waiters.find(id);
        if (found == waiters.end()) {
            // Nothing is waiting for this answer any more. An object answer still has to be released,
            // and only the environment it was created in can do that.
            if (object && value.l != nullptr && callerEnv != nullptr)
                callerEnv->DeleteGlobalRef(value.l);
            return;
        }

        Waiter* waiter = found->second;
        {
            std::lock_guard<std::mutex> lock(waiter->mutex);
            waiter->value = value;
            waiter->object = object;
            waiter->done = true;
        }
        waiter->condition.notify_all();
    }

    // Private, because only the release below may answer the waiters of a pump that stopped.
    static void ReleaseWaiters(const char* reason) {
        std::lock_guard<std::mutex> guard(waiterMutex);
        for (auto& entry : waiters) {
            Waiter* waiter = entry.second;
            {
                std::lock_guard<std::mutex> lock(waiter->mutex);
                if (waiter->done)
                    continue;
                waiter->value = jvalue{};
                waiter->object = false;
                waiter->done = true;
            }
            util::Log::InfoF("BUS", "request answered with the neutral value (%s)", reason);
            waiter->condition.notify_all();
        }
    }

    void Release(JNIEnv*, jclass) {
        {
            // One critical section with the flag: a call that registers takes this same lock, so it is
            // either wholly before - its message is cleared here and its waiter released below - or wholly
            // after, where the flag drops it before anything is queued.
            std::lock_guard<std::mutex> guard(waiterMutex);
            artPumpStopped.store(true);
            Mailbox::Clear(jni::Side::Art);
        }

        // Nothing will perform the calls that are still queued, so waiting for their answers would be
        // waiting forever.
        ReleaseWaiters("the ART pump stopped");
    }

} // namespace copper::bridge::bus::Waiters
