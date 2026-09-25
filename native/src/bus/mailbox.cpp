#include "bus/mailbox.h"
#include "bus/internal/mailbox.h"

#include "gen/binding.h"
#include "gen/vmcall.h"
#include "util/log.h"

#include <deque>
#include <mutex>
#include <utility>

// The mailboxes themselves, and the wake-up that keeps a sleeping side from holding a call back.
//
// One mailbox per side, each with its own lock, so a burst of calls costs one wake-up and the side that
// performs them never holds a lock while a handler runs.

namespace copper::bridge::bus::Mailbox {
    namespace Bus = gen::Bus;

    namespace {

        // What one side's mailbox is: its queue and the flag that keeps a burst from posting a wake-up per
        // message.
        struct Mailbox {
            std::mutex mutex;
            std::deque<Message> queue;
            bool wakePosted = false;
        };

        // One per side, existing with this library rather than with the first message: a call can be queued
        // before either side has pumped anything.
        Mailbox artMailbox;
        Mailbox jvmMailbox;

        Mailbox& MailboxOf(jni::Side side) {
            return side == jni::Side::Art ? artMailbox : jvmMailbox;
        }

        // Whether the line below about the missing ART pump has been written: reported once, because it
        // would otherwise repeat for every wake-up.
        bool pumpMissingReported = false;

        // Wakes the ART main thread. This is the only Java call a JVM thread makes on an ART object, and it
        // is legal because Handler.post is thread safe; the call itself belongs to the binding layer.
        void WakeArt() {
            switch (gen::VmCall::PostRequestPump()) {
            case gen::Binding::Outcome::Done:
                return;
            case gen::Binding::Outcome::NotResolved: {
                // Reported once: the pump's slow tick still picks the call up, so this is late, not lost.
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

        // Woken outside the lock: the wake-up borrows the other side's environment, and holding a lock
        // across a JNI call is how a UI thread ends up waiting on a game thread.
        if (wakeUp)
            Wake(to);
    }

    void BeginDrain(jni::Side me) {
        Mailbox& box = MailboxOf(me);
        std::lock_guard<std::mutex> lock(box.mutex);
        // Cleared before the first take rather than after the last, so anything queued while a drain is
        // running posts a new wake-up instead of assuming one is already on its way.
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

        // The whole queue leaves under the lock and is given back outside it: releasing a payload attaches
        // to the VM that owns it, and a producer must not wait behind that.
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
