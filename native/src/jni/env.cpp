#include "jni/env.h"

#include "jni/state.h"
#include "util/log.h"

#include <pthread.h>

namespace copper::bridge::jni {

    // The two environments of this process, kept beside this file; a side that has not been recorded yet is
    // reported once rather than on every call.

    namespace {

        /** Gives back one thread's attachment when that thread ends. `value` is the VM it was attached to. */
        void DetachAtExit(void* value) {
            auto vm = static_cast<JavaVM*>(value);
            if (vm != nullptr)
                vm->DetachCurrentThread();
        }

        /** The keys that remember which side a thread attached to. One per side, because a thread can be
         *  attached to both - the thread that starts the JVM is a thread of ART's and becomes the JVM's main
         *  thread. Both are created with this library: a thread that attached before a key existed would
         *  leave a record no key can release. */
        struct AttachedKeys {
            pthread_key_t keys[2] = {0, 0};

            AttachedKeys() {
                pthread_key_create(&keys[0], DetachAtExit);
                pthread_key_create(&keys[1], DetachAtExit);
            }
        };

        AttachedKeys attachedKeys;

        /** The key of one side, for the thread that recorded its attachment under it. */
        pthread_key_t AttachedKey(Side side) {
            return attachedKeys.keys[side == Side::Art ? 0 : 1];
        }

        // Whether the line about a missing VM has been written: reported once.
        bool missingVmReported = false;

    } // namespace

    Env::Env(Side side, bool detaches) : detaches(detaches) {
        vm = side == Side::Art ? State::ArtVm() : State::Jvm();
        if (vm == nullptr) {
            if (!missingVmReported) {
                missingVmReported = true;
                util::Log::Warn("JNI", side == Side::Art ? "no ART VM in this copy of the library"
                                                         : "no JVM in this copy of the library");
            }
            return;
        }

        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr)
            return;

        // Attached once and left attached while the thread lives: re-attaching on every call would pay the
        // cost over and over. The record goes in a key whose destructor gives it back when the thread ends.
        if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK || env == nullptr) {
            env = nullptr;
            util::Log::Error("JNI", "cannot attach this thread to the other VM");
            return;
        }
        attached = true;

        if (!detaches)
            pthread_setspecific(AttachedKey(side), vm);
    }

    Env::~Env() {
        // Only what this object attached, and only when the caller said it is not coming back.
        if (attached && detaches && vm != nullptr)
            vm->DetachCurrentThread();
    }

} // namespace copper::bridge::jni
