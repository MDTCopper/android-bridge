#include "bus/mailbox.h"
#include "bus/internal/mailbox.h"

#include "gen/binding.h"
#include "gen/vmcall.h"
#include "util/log.h"

#include <deque>
#include <mutex>
#include <utility>

// The mailboxes, and the wake-up that keeps a sleeping side from holding a call back.

namespace copper::bridge::bus::Mailbox {
    namespace Bus = gen::Bus;

    namespace {

        struct Mailbox {
            std::mutex mutex;
            std::deque<Message> queue;
            bool wakePosted = false;
        };

        Mailbox artMailbox;
        Mailbox jvmMailbox;

        Mailbox& MailboxOf(jni::Side side) {
            return side == jni::Side::Art ? artMailbox : jvmMailbox;
        }

        bool pumpMissingReported = false;

        // Wakes the ART main thread: the only Java call a JVM thread makes on an ART object (Handler.post is thread safe).
        void WakeArt() {
            switch (gen::VmCall::PostRequestPump()) {
            case gen::Binding::Outcome::Done:
                return;
            case gen::Binding::Outcome::NotResolved: {
                if (!pumpMissingReported) {
                    pumpMissingReported = true;
                    util::Log::Info("BUS", "the ART bus was never resolved, so calls wait for its periodic tick");
                }
                return;
            }
            case gen::Binding::Outcome::Failed:
                // Not fatal: the pump still runs on its slow tick, so the call is late rather than lost.
                util::Log::Warn("BUS", "cannot wake the ART pump, falling back to its periodic tick");
                return;
            }
        }

        void Wake(jni::Side to) {
            // Only ART has a message loop that sleeps; the game loop is already turning every frame.
            if (to == jni::Side::Art)
                WakeArt();
        }

    } // namespace

    void Enqueue(jni::Side to, Message&& message) {
        Mailbox& box = MailboxOf(to);
        bool wakeUp = false;
        {
            std::lock_guard<std::mutex> lock(box.mutex);
            box.queue.push_back(std::move(message));
            if (!box.wakePosted) {
                box.wakePosted = true;
                wakeUp = true;
            }
        }

        // Woken outside the lock: holding one across a JNI call is how a UI thread ends up waiting on a game thread.
        if (wakeUp)
            Wake(to);
    }

    void BeginDrain(jni::Side me) {
        Mailbox& box = MailboxOf(me);
        std::lock_guard<std::mutex> lock(box.mutex);
        // Cleared before the first take, not after the last, so anything queued during a drain posts a new wake-up.
        box.wakePosted = false;
    }

    Bus::Kind Take(jni::Side me, Message& out) {
        Mailbox& box = MailboxOf(me);
        std::lock_guard<std::mutex> lock(box.mutex);
        if (box.queue.empty())
            return Bus::Kind::None;
        out = std::move(box.queue.front());
        box.queue.pop_front();
        return out.kind;
    }

    int Clear(jni::Side side) {
        Mailbox& box = MailboxOf(side);

        // The whole queue is given back outside the lock: releasing a payload attaches to the VM that owns it.
        std::deque<Message> abandoned;
        {
            std::lock_guard<std::mutex> lock(box.mutex);
            abandoned.swap(box.queue);
        }

        const int count = static_cast<int>(abandoned.size());
        for (Message& message : abandoned)
            message.Dispose();
        return count;
    }

} // namespace copper::bridge::bus::Mailbox
