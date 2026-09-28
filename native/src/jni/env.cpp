#include "jni/env.h"

#include "jni/state.h"
#include "util/log.h"

#include <pthread.h>

namespace copper::bridge::jni {

    // The two environments of this process: a side that has not been recorded yet is reported once, not per call.

    namespace {

        void DetachAtExit(void* value) {
            auto vm = static_cast<JavaVM*>(value);
            if (vm != nullptr)
                vm->DetachCurrentThread();
        }

        /** The keys remembering which side a thread attached to; one per side, a thread being attachable to both. */
        struct AttachedKeys {
            pthread_key_t keys[2] = {0, 0};

            AttachedKeys() {
                pthread_key_create(&keys[0], DetachAtExit);
                pthread_key_create(&keys[1], DetachAtExit);
            }
        };

        AttachedKeys attachedKeys;

        pthread_key_t AttachedKey(Side side) {
            return attachedKeys.keys[side == Side::Art ? 0 : 1];
        }

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

        // Attached once and left attached while the thread lives; the record goes in a key whose destructor returns it.
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
