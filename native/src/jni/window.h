#ifndef COPPER_BRIDGE_JNI_WINDOW_H
#define COPPER_BRIDGE_JNI_WINDOW_H

#include <jni.h>

namespace copper::bridge::jni {

    // The surface, as native sees it: the conversion to the pointer EGL wants. The activity holds the surface.

    /** Turns the caller's {@code Surface} into the native window behind it, or 0 when there is none. */
    jlong NativeWindow(JNIEnv* env, jobject self, jobject surface);

} // namespace copper::bridge::jni

#endif // COPPER_BRIDGE_JNI_WINDOW_H
