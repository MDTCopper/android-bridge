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

// The waiters: a request is registered before its message is queued, and completed by whoever performs it.

namespace copper::bridge::bus::Waiters {
    namespace Bus = gen::Bus;

    namespace {

        struct Waiter {
            std::mutex mutex;
            std::condition_variable condition;
            bool done = false;
            bool object = false;
            jvalue value{};
        };

        std::mutex waiterMutex;
        std::unordered_map<int64_t, Waiter*> waiters;
        // Counted from one: zero is what a message asking for no answer carries.
        std::atomic<int64_t> nextWaiterId{1};

        int64_t NextWaiterId() {
            return nextWaiterId.fetch_add(1);
        }

        // Whether a side's pump has stopped for good. Only ART is ever marked, the JVM side's pump ending with the process.
        std::atomic<bool> artPumpStopped{false};
        std::atomic<bool> jvmPumpStopped{false};

        bool PumpStopped(jni::Side side) {
            return side == jni::Side::Art ? artPumpStopped.load() : jvmPumpStopped.load();
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
            util::Log::Warn("BUS", buffer);
        }

    } // namespace

    void EnqueueAndWait(jni::Side to, Message&& message, jvalue& answer) {
        answer = jvalue{};

        Waiter waiter;
        const int64_t id = NextWaiterId();
        const Bus::Kind kind = message.kind;
        message.requestId = id;

        // Registered before the message is queued: the answer may otherwise arrive first and be dropped. Queueing
        // shares this critical section with the stop flag, so a call arriving as the pump stops is wholly before or after.
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
            // The pump that would perform this call has stopped for good: the neutral value is the answer.
            message.Dispose();
            return;
        }

        {
            std::unique_lock<std::mutex> lock(waiter.mutex);
            while (!waiter.done) {
                if (waiter.condition.wait_for(lock, std::chrono::seconds(5)) == std::cv_status::timeout
                        && !waiter.done) {
                    // Reported, then waited out: giving up would leave the game in an unknown state.
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
            // The answer was handed over as a global reference, a local one belonging to the thread that made it.
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
            // Nothing is waiting for this answer any more, but an object one still has to be released.
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
            // One critical section with the flag: a registering call takes this same lock.
            std::lock_guard<std::mutex> guard(waiterMutex);
            artPumpStopped.store(true);
            Mailbox::Clear(jni::Side::Art);
        }

        // Nothing will perform the calls still queued, so waiting for their answers would be waiting forever.
        ReleaseWaiters("the ART pump stopped");
    }

} // namespace copper::bridge::bus::Waiters
