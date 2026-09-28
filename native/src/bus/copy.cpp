#include "bus/internal/copy.h"

// Cross-VM copies: one value rebuilt in the other VM, byte for byte where the element type is fixed and element by
// element where it is a reference.

namespace copper::bridge::bus::Copy {

    jstring CopyString(JNIEnv* from, JNIEnv* to, jstring value) {
        if (value == nullptr)
            return nullptr;

        const jsize length = from->GetStringLength(value);
        const jchar* chars = from->GetStringChars(value, nullptr);
        if (chars == nullptr) {
            from->ExceptionClear();
            return nullptr;
        }

        jstring result = to->NewString(chars, length);
        from->ReleaseStringChars(value, chars);
        if (result == nullptr)
            to->ExceptionClear();
        return result;
    }

    jobjectArray CopyStrings(JNIEnv* from, JNIEnv* to, jobjectArray value) {
        if (value == nullptr)
            return nullptr;

        jclass stringClass = to->FindClass("java/lang/String");
        if (stringClass == nullptr) {
            to->ExceptionClear();
            return nullptr;
        }

        const jsize count = from->GetArrayLength(value);
        jobjectArray result = to->NewObjectArray(count, stringClass, nullptr);
        to->DeleteLocalRef(stringClass);
        if (result == nullptr) {
            to->ExceptionClear();
            return nullptr;
        }

        for (jsize i = 0; i < count; i++) {
            auto item = static_cast<jstring>(from->GetObjectArrayElement(value, i));
            if (item == nullptr)
                continue;
            jstring copy = CopyString(from, to, item);
            from->DeleteLocalRef(item);
            if (copy == nullptr)
                continue;
            to->SetObjectArrayElement(result, i, copy);
            to->DeleteLocalRef(copy);
        }
        return result;
    }

    // One template for the eight array kinds: read the elements where they live, build an array of the
    // same length on the other side, and copy the bytes across.
#define BRIDGE_COPY_ARRAY(name, type, suffix)                                                      \
        j##type##Array name(JNIEnv* from, JNIEnv* to, j##type##Array value) {                          \
            if (value == nullptr)                                                                      \
                return nullptr;                                                                        \
            const jsize count = from->GetArrayLength(value);                                           \
            j##type##Array result = to->New##suffix##Array(count);                                     \
            if (result == nullptr) {                                                                   \
                to->ExceptionClear();                                                                  \
                return nullptr;                                                                        \
            }                                                                                          \
            j##type* elements = from->Get##suffix##ArrayElements(value, nullptr);                      \
            if (elements == nullptr) {                                                                 \
                from->ExceptionClear();                                                                \
                return result;                                                                         \
            }                                                                                          \
            to->Set##suffix##ArrayRegion(result, 0, count, elements);                                   \
            from->Release##suffix##ArrayElements(value, elements, JNI_ABORT);                           \
            return result;                                                                             \
        }

    BRIDGE_COPY_ARRAY(CopyBooleans, boolean, Boolean)
    BRIDGE_COPY_ARRAY(CopyBytes, byte, Byte)
    BRIDGE_COPY_ARRAY(CopyChars, char, Char)
    BRIDGE_COPY_ARRAY(CopyShorts, short, Short)
    BRIDGE_COPY_ARRAY(CopyInts, int, Int)
    BRIDGE_COPY_ARRAY(CopyLongs, long, Long)
    BRIDGE_COPY_ARRAY(CopyFloats, float, Float)
    BRIDGE_COPY_ARRAY(CopyDoubles, double, Double)

    #undef BRIDGE_COPY_ARRAY

    jvalue CopyValue(JNIEnv* from, JNIEnv* to, char code, const jvalue& in) {
        jvalue out{};
        switch (code) {
            case 'V': break;
            case 'Z': out.z = in.z != JNI_FALSE ? JNI_TRUE : JNI_FALSE; break;
            case 'B': out.b = in.b; break;
            case 'C': out.c = in.c; break;
            case 'S': out.s = in.s; break;
            case 'I': out.i = in.i; break;
            case 'J': out.j = in.j; break;
            case 'F': out.f = in.f; break;
            case 'D': out.d = in.d; break;
            case 'L': out.l = CopyString(from, to, static_cast<jstring>(in.l)); break;
            case 'l': out.l = CopyStrings(from, to, static_cast<jobjectArray>(in.l)); break;
            case 'z': out.l = CopyBooleans(from, to, static_cast<jbooleanArray>(in.l)); break;
            case 'b': out.l = CopyBytes(from, to, static_cast<jbyteArray>(in.l)); break;
            case 'c': out.l = CopyChars(from, to, static_cast<jcharArray>(in.l)); break;
            case 's': out.l = CopyShorts(from, to, static_cast<jshortArray>(in.l)); break;
            case 'i': out.l = CopyInts(from, to, static_cast<jintArray>(in.l)); break;
            case 'j': out.l = CopyLongs(from, to, static_cast<jlongArray>(in.l)); break;
            case 'f': out.l = CopyFloats(from, to, static_cast<jfloatArray>(in.l)); break;
            case 'd': out.l = CopyDoubles(from, to, static_cast<jdoubleArray>(in.l)); break;
            default: break;
        }
        return out;
    }

} // namespace copper::bridge::bus::Copy
