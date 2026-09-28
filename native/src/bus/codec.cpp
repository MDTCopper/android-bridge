#include "bus/codec.h"
#include "bus/internal/codec.h"

#include "bus/internal/copy.h"
#include "bus/internal/row.h"
#include "jni/env.h"
#include "bus/message.h"
#include "bus/internal/type_codes.h"
#include "util/log.h"

#include <cstring>

// The payload codec: a row's payload is described entirely by its type codes, so nothing here names a type.

namespace copper::bridge::bus::Codec {
    namespace Bus = gen::Bus;

    namespace {

        uint8_t* PutU32(uint8_t* out, uint32_t value) {
            memcpy(out, &value, sizeof(value));
            return out + sizeof(value);
        }

        const uint8_t* GetU32(const uint8_t* in, uint32_t& value) {
            memcpy(&value, in, sizeof(value));
            return in + sizeof(value);
        }

        size_t ElementWidth(char code) {
            switch (code) {
                case 'z':
                case 'b': return 1;
                case 'c':
                case 's': return 2;
                case 'i':
                case 'f': return 4;
                case 'j':
                case 'd': return 8;
                default: return 0;
            }
        }

        void* ArrayElements(JNIEnv* env, char code, jarray array) {
            switch (code) {
                case 'z': return env->GetBooleanArrayElements(reinterpret_cast<jbooleanArray>(array), nullptr);
                case 'b': return env->GetByteArrayElements(reinterpret_cast<jbyteArray>(array), nullptr);
                case 'c': return env->GetCharArrayElements(reinterpret_cast<jcharArray>(array), nullptr);
                case 's': return env->GetShortArrayElements(reinterpret_cast<jshortArray>(array), nullptr);
                case 'i': return env->GetIntArrayElements(reinterpret_cast<jintArray>(array), nullptr);
                case 'j': return env->GetLongArrayElements(reinterpret_cast<jlongArray>(array), nullptr);
                case 'f': return env->GetFloatArrayElements(reinterpret_cast<jfloatArray>(array), nullptr);
                case 'd': return env->GetDoubleArrayElements(reinterpret_cast<jdoubleArray>(array), nullptr);
                default: return nullptr;
            }
        }

        void ReleaseElements(JNIEnv* env, char code, jarray array, void* elements) {
            switch (code) {
                case 'z': env->ReleaseBooleanArrayElements(reinterpret_cast<jbooleanArray>(array), static_cast<jboolean*>(elements), JNI_ABORT); break;
                case 'b': env->ReleaseByteArrayElements(reinterpret_cast<jbyteArray>(array), static_cast<jbyte*>(elements), JNI_ABORT); break;
                case 'c': env->ReleaseCharArrayElements(reinterpret_cast<jcharArray>(array), static_cast<jchar*>(elements), JNI_ABORT); break;
                case 's': env->ReleaseShortArrayElements(reinterpret_cast<jshortArray>(array), static_cast<jshort*>(elements), JNI_ABORT); break;
                case 'i': env->ReleaseIntArrayElements(reinterpret_cast<jintArray>(array), static_cast<jint*>(elements), JNI_ABORT); break;
                case 'j': env->ReleaseLongArrayElements(reinterpret_cast<jlongArray>(array), static_cast<jlong*>(elements), JNI_ABORT); break;
                case 'f': env->ReleaseFloatArrayElements(reinterpret_cast<jfloatArray>(array), static_cast<jfloat*>(elements), JNI_ABORT); break;
                case 'd': env->ReleaseDoubleArrayElements(reinterpret_cast<jdoubleArray>(array), static_cast<jdouble*>(elements), JNI_ABORT); break;
                default: break;
            }
        }

        jarray NewArray(JNIEnv* env, char code, jsize length) {
            switch (code) {
                case 'z': return env->NewBooleanArray(length);
                case 'b': return env->NewByteArray(length);
                case 'c': return env->NewCharArray(length);
                case 's': return env->NewShortArray(length);
                case 'i': return env->NewIntArray(length);
                case 'j': return env->NewLongArray(length);
                case 'f': return env->NewFloatArray(length);
                case 'd': return env->NewDoubleArray(length);
                default: return nullptr;
            }
        }

        void SetArrayRegion(JNIEnv* env, char code, jarray array, jsize length, const void* source) {
            switch (code) {
                case 'z': env->SetBooleanArrayRegion(reinterpret_cast<jbooleanArray>(array), 0, length, static_cast<const jboolean*>(source)); break;
                case 'b': env->SetByteArrayRegion(reinterpret_cast<jbyteArray>(array), 0, length, static_cast<const jbyte*>(source)); break;
                case 'c': env->SetCharArrayRegion(reinterpret_cast<jcharArray>(array), 0, length, static_cast<const jchar*>(source)); break;
                case 's': env->SetShortArrayRegion(reinterpret_cast<jshortArray>(array), 0, length, static_cast<const jshort*>(source)); break;
                case 'i': env->SetIntArrayRegion(reinterpret_cast<jintArray>(array), 0, length, static_cast<const jint*>(source)); break;
                case 'j': env->SetLongArrayRegion(reinterpret_cast<jlongArray>(array), 0, length, static_cast<const jlong*>(source)); break;
                case 'f': env->SetFloatArrayRegion(reinterpret_cast<jfloatArray>(array), 0, length, static_cast<const jfloat*>(source)); break;
                case 'd': env->SetDoubleArrayRegion(reinterpret_cast<jdoubleArray>(array), 0, length, static_cast<const jdouble*>(source)); break;
                default: break;
            }
        }

        void WriteScalar(jint* args, int& cursor, char code, const jvalue& value) {
            switch (code) {
                case 'J': {
                    // The value's own bytes, and the reader takes them back the same way, so neither side names a byte order.
                    memcpy(&args[cursor], &value.j, sizeof(value.j));
                    cursor += sizeof(value.j) / sizeof(*args);
                    break;
                }
                case 'D': {
                    memcpy(&args[cursor], &value.d, sizeof(value.d));
                    cursor += sizeof(value.d) / sizeof(*args);
                    break;
                }
                case 'Z': args[cursor++] = value.z != JNI_FALSE ? 1 : 0; break;
                case 'B': args[cursor++] = static_cast<jint>(value.b); break;
                case 'C': args[cursor++] = static_cast<jint>(value.c); break;
                case 'S': args[cursor++] = static_cast<jint>(value.s); break;
                case 'F': {
                    jint bits = 0;
                    memcpy(&bits, &value.f, sizeof(bits));
                    args[cursor++] = bits;
                    break;
                }
                default: args[cursor++] = value.i; break;
            }
        }

        // How many bytes one object parameter takes in the box. A null is spelled 0xffffffff; a zero length is empty.
        constexpr uint32_t BOXED_NULL = 0xffffffffu;

        size_t BoxedSize(JNIEnv* env, char code, const jvalue& value) {
            if (value.l == nullptr)
                return 4;

            if (code == 'L') {
                const jsize length = env->GetStringLength(static_cast<jstring>(value.l));
                return 4 + 2 * static_cast<size_t>(length);
            }
            if (code == 'l') {
                auto array = static_cast<jobjectArray>(value.l);
                const jsize count = env->GetArrayLength(array);
                size_t total = 4;
                for (jsize i = 0; i < count; i++) {
                    auto item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
                    total += 4 + (item == nullptr ? 0 : 2 * static_cast<size_t>(env->GetStringLength(item)));
                    if (item != nullptr)
                        env->DeleteLocalRef(item);
                }
                return total;
            }
            return 4 + ElementWidth(code) * static_cast<size_t>(env->GetArrayLength(static_cast<jarray>(value.l)));
        }

        /**
         * Writes one object parameter at `out`, never at or past `end`.
         * The lengths are read here, not from {@link BoxedSize}: a box resized under the writer loses its tail.
         */
        uint8_t* WriteBoxed(JNIEnv* env, uint8_t* out, uint8_t* end, char code, const jvalue& value) {
            if (value.l == nullptr)
                return end - out >= 4 ? PutU32(out, BOXED_NULL) : out;

            if (code == 'L') {
                auto text = static_cast<jstring>(value.l);
                const jsize length = env->GetStringLength(text);
                const size_t bytes = 4 + 2 * static_cast<size_t>(length);
                if (static_cast<size_t>(end - out) < bytes)
                    return out;
                out = PutU32(out, static_cast<uint32_t>(length));
                const jchar* chars = env->GetStringChars(text, nullptr);
                if (chars != nullptr) {
                    memcpy(out, chars, 2 * static_cast<size_t>(length));
                    env->ReleaseStringChars(text, chars);
                } else {
                    env->ExceptionClear();
                    memset(out, 0, 2 * static_cast<size_t>(length));
                }
                return out + 2 * static_cast<size_t>(length);
            }

            if (code == 'l') {
                auto array = static_cast<jobjectArray>(value.l);
                const jsize count = env->GetArrayLength(array);
                if (end - out < 4)
                    return out;
                uint8_t* header = out;
                out = PutU32(out, static_cast<uint32_t>(count));
                // A count is written before the items it counts: the array's own count would make the peer read past the box.
                size_t written = 0;
                for (jsize i = 0; i < count; i++) {
                    auto item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
                    if (item == nullptr) {
                        if (end - out < 4)
                            break;
                        out = PutU32(out, BOXED_NULL);
                        written++;
                        continue;
                    }
                    const jsize length = env->GetStringLength(item);
                    const size_t bytes = 4 + 2 * static_cast<size_t>(length);
                    if (static_cast<size_t>(end - out) < bytes) {
                        env->DeleteLocalRef(item);
                        break;
                    }
                    out = PutU32(out, static_cast<uint32_t>(length));
                    const jchar* chars = env->GetStringChars(item, nullptr);
                    if (chars != nullptr) {
                        memcpy(out, chars, 2 * static_cast<size_t>(length));
                        env->ReleaseStringChars(item, chars);
                    } else {
                        env->ExceptionClear();
                        memset(out, 0, 2 * static_cast<size_t>(length));
                    }
                    out += 2 * static_cast<size_t>(length);
                    env->DeleteLocalRef(item);
                    written++;
                }
                if (written != static_cast<size_t>(count))
                    memcpy(header, &written, sizeof(uint32_t));
                return out;
            }

            auto array = static_cast<jarray>(value.l);
            const jsize count = env->GetArrayLength(array);
            const size_t width = ElementWidth(code);
            const size_t bytes = 4 + width * static_cast<size_t>(count);
            if (static_cast<size_t>(end - out) < bytes)
                return out;
            out = PutU32(out, static_cast<uint32_t>(count));
            void* elements = ArrayElements(env, code, array);
            if (elements != nullptr) {
                memcpy(out, elements, width * static_cast<size_t>(count));
                ReleaseElements(env, code, array, elements);
            } else {
                env->ExceptionClear();
                memset(out, 0, width * static_cast<size_t>(count));
            }
            return out + width * static_cast<size_t>(count);
        }

        jobject ReadBoxed(JNIEnv* env, const uint8_t*& cursor, char code) {
            uint32_t length = 0;
            cursor = GetU32(cursor, length);
            if (length == BOXED_NULL)
                return nullptr;

            if (code == 'L') {
                jstring text = env->NewString(reinterpret_cast<const jchar*>(cursor), static_cast<jsize>(length));
                if (text == nullptr)
                    env->ExceptionClear();
                cursor += 2 * static_cast<size_t>(length);
                return text;
            }

            if (code == 'l') {
                jclass stringClass = env->FindClass("java/lang/String");
                if (stringClass == nullptr) {
                    env->ExceptionClear();
                    return nullptr;
                }
                jobjectArray array = env->NewObjectArray(static_cast<jsize>(length), stringClass, nullptr);
                env->DeleteLocalRef(stringClass);
                if (array == nullptr) {
                    env->ExceptionClear();
                    return nullptr;
                }
                for (uint32_t i = 0; i < length; i++) {
                    uint32_t itemLength = 0;
                    cursor = GetU32(cursor, itemLength);
                    if (itemLength == BOXED_NULL)
                        continue;
                    jstring item = env->NewString(reinterpret_cast<const jchar*>(cursor),
                                                  static_cast<jsize>(itemLength));
                    cursor += 2 * static_cast<size_t>(itemLength);
                    if (item == nullptr) {
                        env->ExceptionClear();
                        continue;
                    }
                    env->SetObjectArrayElement(array, static_cast<jsize>(i), item);
                    env->DeleteLocalRef(item);
                }
                return array;
            }

            const size_t width = ElementWidth(code);
            jarray array = NewArray(env, code, static_cast<jsize>(length));
            if (array == nullptr) {
                env->ExceptionClear();
            } else {
                SetArrayRegion(env, code, array, static_cast<jsize>(length), cursor);
            }
            cursor += width * static_cast<size_t>(length);
            return array;
        }

        // The asynchronous half: measure everything, allocate once, write once, without touching the target VM.
        void PutBoxed(JNIEnv* from, Message& message, const jvalue* values, int count) {
            const Bus::CallEntry* entry = Bus::FindCall(message.kind);
            const char* codes = entry == nullptr ? "" : entry->param_codes;

            size_t total = 1;
            for (int i = 0; codes[i] != '\0' && i < count; i++) {
                if (!TypeCodes::IsObject(codes[i]))
                    continue;
                total += BoxedSize(from, codes[i], values[i]);
            }

            message.box.data = new uint8_t[total];
            message.box.size = static_cast<uint32_t>(total);

            // The box is the measured size and the writing is bounded by it; the objects it was measured from are live.
            uint8_t* out = message.box.data + 1;
            uint8_t* end = message.box.data + total;
            int written = 0;
            for (int i = 0; codes[i] != '\0' && i < count; i++) {
                if (!TypeCodes::IsObject(codes[i]))
                    continue;
                uint8_t* next = WriteBoxed(from, out, end, codes[i], values[i]);
                if (next != out)
                    written++;
                out = next;
            }

            // Written from what went in rather than what was measured, so the peer walks exactly what is there.
            message.box.data[0] = static_cast<uint8_t>(written);
        }

        // The synchronous half: the object is rebuilt in the target VM and pinned there for the handler.
        jobject PutTarget(JNIEnv* from, jni::Side target, char code, const jvalue& value) {
            if (value.l == nullptr)
                return nullptr;

            jni::Env targetEnv(target);
            JNIEnv* to = targetEnv.Get();
            if (to == nullptr)
                return nullptr;

            jvalue copy = Copy::CopyValue(from, to, code, value);
            if (copy.l == nullptr)
                return nullptr;

            jobject global = to->NewGlobalRef(copy.l);
            to->DeleteLocalRef(copy.l);
            if (global == nullptr)
                to->ExceptionClear();
            return global;
        }

    } // namespace

    jvalue ReadScalar(const jint* args, int& cursor, char code) {
        jvalue value{};
        switch (code) {
            case 'J': {
                // A value's bytes, laid down as the writer did - and as a jlong sits in memory on every ABI here.
                memcpy(&value.j, &args[cursor], sizeof(value.j));
                cursor += sizeof(value.j) / sizeof(*args);
                break;
            }
            case 'D': {
                memcpy(&value.d, &args[cursor], sizeof(value.d));
                cursor += sizeof(value.d) / sizeof(*args);
                break;
            }
            case 'Z': value.z = args[cursor++] != 0 ? JNI_TRUE : JNI_FALSE; break;
            case 'B': value.b = static_cast<jbyte>(args[cursor++]); break;
            case 'C': value.c = static_cast<jchar>(args[cursor++]); break;
            case 'S': value.s = static_cast<jshort>(args[cursor++]); break;
            case 'F': {
                const jint bits = args[cursor++];
                memcpy(&value.f, &bits, sizeof(value.f));
                break;
            }
            default: value.i = args[cursor++]; break;
        }
        return value;
    }

    void DecodeBoxed(JNIEnv* env, const Message& message, jobject* out) {
        if (message.box.data == nullptr)
            return;

        const Bus::CallEntry* entry = Bus::FindCall(message.kind);
        const char* codes = entry == nullptr ? "" : entry->param_codes;

        const uint8_t* cursor = message.box.data + 1;
        int index = 0;
        for (int i = 0; codes[i] != '\0'; i++) {
            if (!TypeCodes::IsObject(codes[i]))
                continue;
            if (static_cast<uint32_t>(index) >= static_cast<uint32_t>(message.box.data[0]))
                return;
            out[index++] = ReadBoxed(env, cursor, codes[i]);
        }
    }

    void PutPayload(JNIEnv* from, Message& message, const jvalue* values, int count) {
        const Bus::CallEntry* entry = Bus::FindCall(message.kind);
        if (entry == nullptr) {
            util::Log::InfoF("BUS", "%s has no row to encode", Bus::NameOf(message.kind));
            return;
        }

        const char* codes = entry->param_codes;

        // Scalars are written on both paths: the same fixed-width slots either way.
        int args = 0;
        for (int i = 0; i < count && codes[i] != '\0'; i++) {
            if (!TypeCodes::IsObject(codes[i]))
                WriteScalar(message.args, args, codes[i], values[i]);
        }

        if (!Row::IsSync(message.kind)) {
            PutBoxed(from, message, values, count);
            return;
        }

        int refs = 0;
        for (int i = 0; i < count && codes[i] != '\0'; i++) {
            if (TypeCodes::IsObject(codes[i]))
                message.refs[refs++] = PutTarget(from, entry->target, codes[i], values[i]);
        }
    }

} // namespace copper::bridge::bus::Codec
