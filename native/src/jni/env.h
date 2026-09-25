#ifndef COPPER_BRIDGE_JNI_ENV_H
#define COPPER_BRIDGE_JNI_ENV_H

#include "jni/side.h"

#include <jni.h>

namespace copper::bridge::jni {

    /**
     * One thread's environment for one virtual machine, attached for as long as this object lives.
     *
     * <p>Two VMs share this library, so every crossing has to name the side it is about to touch. A thread
     * that comes back keeps its attachment until it ends, released from a key destructor on that thread; a
     * thread that parks for good never reaches its own end, so it asks for {@code detaches} and gives the
     * record back before it stops.</p>
     */
    class Env {
    public:
        /** The other side: the owner of a row's handler is the side the pump performs it on. */
        static Side Other(Side side) {
            return side == Side::Art ? Side::Jvm : Side::Art;
        }

        /**
         * Attaches this thread to `side`, unless it is attached already.
         *
         * @param detaches whether to give the attachment back when this object dies, rather than leaving
         *                 it for the thread's own end
         */
        Env(Side side, bool detaches = false);
        ~Env();

        Env(const Env&) = delete;
        Env& operator=(const Env&) = delete;

        /** The environment, or nullptr when this thread could not be attached to that side. */
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
