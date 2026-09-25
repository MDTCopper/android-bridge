#ifndef COPPER_BRIDGE_JNI_WINDOW_H
#define COPPER_BRIDGE_JNI_WINDOW_H

#include <jni.h>

namespace copper::bridge::jni {

    // The surface, as native sees it. The ART side owns the surface, and what the JVM side needs from it is a
    // pointer it can hand to EGL - so the one thing here is the conversion, and the pointer travels on as a
    // plain number. Nothing in this module keeps it: the activity holds the surface.

    /** Turns the caller's {@code Surface} into the native window behind it, or 0 when there is none. */
    jlong NativeWindow(JNIEnv* env, jobject self, jobject surface);

} // namespace copper::bridge::jni

#endif // COPPER_BRIDGE_JNI_WINDOW_H
