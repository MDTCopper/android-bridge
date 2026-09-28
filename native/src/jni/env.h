#ifndef COPPER_BRIDGE_JNI_ENV_H
#define COPPER_BRIDGE_JNI_ENV_H

#include "jni/side.h"

#include <jni.h>

namespace copper::bridge::jni {

    /** One thread's environment for one virtual machine, attached for as long as this object lives; every crossing
     *  names the side it touches. A thread that parks for good gives the record back before it stops. */
    class Env {
    public:
        static Side Other(Side side) {
            return side == Side::Art ? Side::Jvm : Side::Art;
        }

        /** Attaches this thread to `side`, unless it is attached already. {@code detaches} gives it back on death. */
        Env(Side side, bool detaches = false);
        ~Env();

        Env(const Env&) = delete;
        Env& operator=(const Env&) = delete;

        /** The environment, or nullptr when this thread could not be attached. */
        JNIEnv* Get() const {
            return env;
        }

        bool Ok() const {
            return env != nullptr;
        }

    private:
        JavaVM* vm = nullptr;
        JNIEnv* env = nullptr;
        bool attached = false;
        bool detaches = false;
    };

} // namespace copper::bridge::jni

#endif // COPPER_BRIDGE_JNI_ENV_H
