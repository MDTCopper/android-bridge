#ifndef COPPER_BRIDGE_BUS_INTERNAL_COPY_H
#define COPPER_BRIDGE_BUS_INTERNAL_COPY_H

#include <jni.h>

// Moving one value from one VM into the other.
//
// The two environments are parameters rather than a fixed direction, which is what lets one implementation
// serve both ways, and nothing here knows a Java class: the row's type code says what the value is. A
// reference is always rebuilt in the target VM, never handed across as it is.

namespace copper::bridge::bus::Copy {

    /** Cross-VM copies. `from` owns the value, `to` gets an equivalent one as a local reference which
     *  the caller owns. */
    jstring CopyString(JNIEnv* from, JNIEnv* to, jstring value);
    jobjectArray CopyStrings(JNIEnv* from, JNIEnv* to, jobjectArray value);
    jbooleanArray CopyBooleans(JNIEnv* from, JNIEnv* to, jbooleanArray value);
    jbyteArray CopyBytes(JNIEnv* from, JNIEnv* to, jbyteArray value);
    jcharArray CopyChars(JNIEnv* from, JNIEnv* to, jcharArray value);
    jshortArray CopyShorts(JNIEnv* from, JNIEnv* to, jshortArray value);
    jintArray CopyInts(JNIEnv* from, JNIEnv* to, jintArray value);
    jlongArray CopyLongs(JNIEnv* from, JNIEnv* to, jlongArray value);
    jfloatArray CopyFloats(JNIEnv* from, JNIEnv* to, jfloatArray value);
    jdoubleArray CopyDoubles(JNIEnv* from, JNIEnv* to, jdoubleArray value);

    /** Rebuilds one value in `to`'s VM from a value that lives in `from`'s, driven by the row's type
     *  code. Arguments and results are the same operation with the two environments swapped. */
    jvalue CopyValue(JNIEnv* from, JNIEnv* to, char code, const jvalue& in);

} // namespace copper::bridge::bus::Copy

#endif // COPPER_BRIDGE_BUS_INTERNAL_COPY_H
