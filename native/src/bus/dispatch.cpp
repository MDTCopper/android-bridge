#include "bus/dispatch.h"

#include "bus/internal/codec.h"
#include "bus/internal/copy.h"
#include "jni/env.h"
#include "bus/internal/handlers.h"
#include "bus/internal/mailbox.h"
#include "bus/internal/type_codes.h"
#include "bus/internal/waiters.h"
#include "util/log.h"

#include <cstring>
#include <utility>

// Performing a call: the pump that drains one side's whole mailbox, and the two ways a single row can run.
//
// A posted row is taken off the queue and performed here, on the thread that pumps; a direct row is
// performed on the calling thread with no queue and no wake-up. The mailbox lock is never held while a
// handler runs - it may take its time, and a thread that queues another message meanwhile must not be
// made to wait for it.

namespace copper::bridge::bus::Dispatch {
    namespace Bus = gen::Bus;

        namespace Log = util::Log;

    namespace {

        /** Makes the call and hands back the raw result; all copying is `CopyValue`'s job. A null target
         *  means a static method, which is how answers are delivered. */
        jvalue CallTarget(JNIEnv* env, char return_code, jobject target, jclass clazz, jmethodID method,
                          const jvalue* args, int count);

        /** Performs one posted row: decodes its payload, calls the handler, handles what comes back. */
        void InvokePosted(JNIEnv* env, const Bus::CallEntry& entry, jobject target, const Message& message) {
            jmethodID method = Handlers::MethodOf(entry.target, entry.kind);
            jclass owner = nullptr;
            if (entry.is_static)
                owner = Handlers::ResolveOwner(entry.target, entry);

            if (method == nullptr || (entry.is_static && owner == nullptr)) {
                Log::InfoF("BUS", "no handler bound for kind %s", Bus::NameOf(entry.kind));
                if (entry.return_code != 'V')
                    Waiters::Complete(message.requestId, jvalue{}, false, nullptr);
                return;
            }

            // The arguments, in the order the row declares them: the request id first when the row carries
            // one, then - for an answer - the kind, then the payload.
            jvalue converted[Bus::MAX_PARAMS];
            int count = 0;
            if (entry.leading_request)
                converted[count++].j = message.requestId;
            if (entry.channel == Bus::Channel::Answer)
                converted[count++].i = static_cast<jint>(entry.kind);

            const bool async = entry.return_code == 'V';
            jobject objects[Bus::MAX_BOXED] = {};
            if (async)
                Codec::DecodeBoxed(env, message, objects);

            int args = 0;
            int refs = 0;
            int boxed = 0;
            for (int i = 0; entry.param_codes[i] != '\0'; i++) {
                const char code = entry.param_codes[i];
                if (!TypeCodes::IsObject(code)) {
                    converted[count++] = Codec::ReadScalar(message.args, args, code);
                } else if (async) {
                    converted[count++].l = objects[boxed++];
                } else {
                    converted[count++].l = message.refs[refs++];
                }
            }

            const jvalue raw = CallTarget(env, entry.return_code, entry.is_static ? nullptr : target, owner,
                                          method, converted, count);

            if (entry.return_code == 'V') {
                if (entry.channel != Bus::Channel::Answer && env->ExceptionCheck()) {
                    if (entry.target == jni::Side::Art) {
                        // An Android handler that throws must not take the game down.
                        Log::InfoF("BUS", "handler %s threw", entry.method_name);
                        env->ExceptionClear();
                    }
                    // On the JVM side the exception is left pending: the game loop gets the real stack.
                }
                return;
            }

            // A synchronous row: the caller is blocked on this request id, so it is completed here, with the
            // answer when there is one and the neutral value on every path that has none.
            if (env->ExceptionCheck()) {
                Log::InfoF("BUS", "handler %s threw", entry.method_name);
                env->ExceptionClear();
                Waiters::Complete(message.requestId, jvalue{}, false, nullptr);
                return;
            }

            if (!TypeCodes::IsObject(entry.return_code)) {
                Waiters::Complete(message.requestId, raw, false, nullptr);
                return;
            }

            // An object answer is the most expensive crossing there is: it is rebuilt in the VM of the
            // waiting caller and handed over as a global reference, because a local one belongs to this
            // thread.
            jni::Side callerSide = jni::Env::Other(entry.target);
            jni::Env callerEnv(callerSide);
            JNIEnv* caller = callerEnv.Get();
            if (caller == nullptr || raw.l == nullptr) {
                Waiters::Complete(message.requestId, jvalue{}, false, nullptr);
                return;
            }

            jvalue copy = Copy::CopyValue(env, caller, entry.return_code, raw);
            jvalue handed{};
            handed.l = copy.l == nullptr ? nullptr : caller->NewGlobalRef(copy.l);
            if (copy.l != nullptr)
                caller->DeleteLocalRef(copy.l);
            if (handed.l == nullptr)
                caller->ExceptionClear();
            Waiters::Complete(message.requestId, handed, handed.l != nullptr, caller);
        }

        jvalue CallTarget(JNIEnv* env, char return_code, jobject target, jclass clazz, jmethodID method,
                          const jvalue* args, int count) {
            (void) count;
            jvalue result{};
            if (target == nullptr && clazz == nullptr)
                return result;

            switch (return_code) {
                case 'V':
                    if (target != nullptr)
                        env->CallVoidMethodA(target, method, args);
                    else
                        env->CallStaticVoidMethodA(clazz, method, args);
                    break;
                case 'Z':
                    result.z = target != nullptr ? env->CallBooleanMethodA(target, method, args)
                                                 : env->CallStaticBooleanMethodA(clazz, method, args);
                    break;
                case 'B':
                    result.b = target != nullptr ? env->CallByteMethodA(target, method, args)
                                                 : env->CallStaticByteMethodA(clazz, method, args);
                    break;
                case 'C':
                    result.c = target != nullptr ? env->CallCharMethodA(target, method, args)
                                                 : env->CallStaticCharMethodA(clazz, method, args);
                    break;
                case 'S':
                    result.s = target != nullptr ? env->CallShortMethodA(target, method, args)
                                                 : env->CallStaticShortMethodA(clazz, method, args);
                    break;
                case 'I':
                    result.i = target != nullptr ? env->CallIntMethodA(target, method, args)
                                                 : env->CallStaticIntMethodA(clazz, method, args);
                    break;
                case 'J':
                    result.j = target != nullptr ? env->CallLongMethodA(target, method, args)
                                                 : env->CallStaticLongMethodA(clazz, method, args);
                    break;
                case 'F': {
                    const jfloat value = target != nullptr ? env->CallFloatMethodA(target, method, args)
                                                           : env->CallStaticFloatMethodA(clazz, method, args);
                    memcpy(&result.i, &value, sizeof(result.i));
                    break;
                }
                case 'D': {
                    const jdouble value = target != nullptr ? env->CallDoubleMethodA(target, method, args)
                                                            : env->CallStaticDoubleMethodA(clazz, method, args);
                    memcpy(&result.j, &value, sizeof(result.j));
                    break;
                }
                default:
                    result.l = target != nullptr ? env->CallObjectMethodA(target, method, args)
                                                 : env->CallStaticObjectMethodA(clazz, method, args);
                    break;
            }
            return result;
        }

        void PumpSide(jni::Side side, JNIEnv* env) {
            Mailbox::BeginDrain(side);

            Message message;
            for (;;) {
                const Bus::Kind kind = Mailbox::Take(side, message);
                if (kind == Bus::Kind::None)
                    return;

                const Bus::CallEntry* entry = Bus::FindCall(kind);
                if (entry == nullptr) {
                    Log::InfoF("BUS", "unexpected kind on the %s queue: %s", side == jni::Side::Art ? "ART" : "JVM",
                         Bus::NameOf(kind));
                    message.Dispose();
                    continue;
                }

                jobject target = entry->is_static ? nullptr : Handlers::BoundInstance(side, kind);
                if (!entry->is_static && target == nullptr) {
                    Log::InfoF("BUS", "no handler bound for kind %s", Bus::NameOf(kind));
                    if (entry->return_code != 'V')
                        Waiters::Complete(message.requestId, jvalue{}, false, nullptr);
                    message.Dispose();
                    continue;
                }

                InvokePosted(env, *entry, target, message);
                message.Dispose();
            }
        }

    } // namespace

    void InvokeDirect(JNIEnv* env, Bus::Kind kind, const jvalue* args, int count, jvalue* out) {
        if (out != nullptr)
            *out = jvalue{};

        const Bus::DirectEntry* entry = Bus::FindDirect(kind);
        if (entry == nullptr) {
            Log::InfoF("BUS", "%s is not a direct row", Bus::NameOf(kind));
            return;
        }

        jni::Env targetEnv(entry->target);
        JNIEnv* to = targetEnv.Get();
        if (to == nullptr)
            return;

        jobject target = Handlers::BoundInstance(entry->target, kind);
        jmethodID method = Handlers::MethodOf(entry->target, kind);
        if (target == nullptr || method == nullptr) {
            Log::InfoF("BUS", "no handler bound for kind %s", Bus::NameOf(kind));
            return;
        }

        // A local reference frame is only worth its cost when the call actually creates references.
        const bool needsFrame = TypeCodes::ObjectCount(entry->param_codes) > 0 || TypeCodes::IsObject(entry->return_code);
        if (needsFrame && to->PushLocalFrame(Bus::MAX_PARAMS + 2) != JNI_OK) {
            to->ExceptionClear();
            Log::InfoF("BUS", "out of local references for kind %s", Bus::NameOf(kind));
            return;
        }

        jvalue converted[Bus::MAX_PARAMS];
        for (int i = 0; i < count; i++)
            converted[i] = Copy::CopyValue(env, to, entry->param_codes[i], args[i]);

        const jvalue raw = CallTarget(to, entry->return_code, target, nullptr, method, converted, count);

        if (to->ExceptionCheck()) {
            Log::InfoF("BUS", "handler %s threw", entry->method_name);
            to->ExceptionClear();
        } else if (out != nullptr) {
            *out = Copy::CopyValue(to, env, entry->return_code, raw);
        }

        if (needsFrame)
            to->PopLocalFrame(nullptr);
    }

    void Pump(JNIEnv* env, jclass) {
        // The game loop's entry point: it performs what was addressed to this side, and nothing else. The
        // other queue belongs to the other VM's loop (`Drain`): an ART handler may touch views and activity
        // state, so it has to run on the thread that owns them.
        PumpSide(jni::Side::Jvm, env);
    }

    void Drain(JNIEnv* env, jclass) {
        PumpSide(jni::Side::Art, env);
    }

} // namespace copper::bridge::bus::Dispatch
