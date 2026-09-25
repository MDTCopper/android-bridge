#include "bus/handlers.h"
#include "bus/internal/handlers.h"

#include "jni/env.h"
#include "util/jni.h"
#include "util/log.h"

#include <string>
#include <vector>

// The binding table, and the walk that fills it.
//
// Which side an object is bound on is not guessed: the entry the VM called is this side's, so one call
// fills one side's table. A type that declares no row of this side costs one verbose line, not an error.

namespace copper::bridge::bus::Handlers {
    namespace Bus = gen::Bus;

    namespace Log = util::Log;

    using jni::Side;

    namespace {

        // What one side has bound: per kind, the instance and its method id, and for a static row the class
        // that declares it. A static row has no instance - it is declared on the class that made the
        // request - so its owning class is resolved from the table instead.
        struct Handlers {
            jobject instances[Bus::KIND_COUNT] = {};
            jmethodID methods[Bus::KIND_COUNT] = {};
            jclass staticOwners[Bus::KIND_COUNT] = {};
            // The declaring classes that already have an instance.
            std::vector<std::string> owners;
        };

        // One per side, existing with this library rather than with the first bind: a bind may arrive before
        // either side has pumped anything.
        Handlers artHandlers;
        Handlers jvmHandlers;

        Handlers& HandlersOf(jni::Side side) {
            return side == jni::Side::Art ? artHandlers : jvmHandlers;
        }

        std::string ClassName(JNIEnv* env, jclass clazz) {
            jclass classClass = env->FindClass("java/lang/Class");
            if (classClass == nullptr) {
                env->ExceptionClear();
                return std::string();
            }
            jmethodID getName = env->GetMethodID(classClass, "getName", "()Ljava/lang/String;");
            env->DeleteLocalRef(classClass);
            if (getName == nullptr) {
                env->ExceptionClear();
                return std::string();
            }

            auto name = static_cast<jstring>(env->CallObjectMethod(clazz, getName));
            if (name == nullptr) {
                env->ExceptionClear();
                return std::string();
            }
            std::string text = util::Jni::ToString(env, name);
            env->DeleteLocalRef(name);
            return text;
        }

        // Every type the instance is, so a handler declared on an interface is found through the class that
        // implements it. This is what lets a branch bind `BridgeEvents implements Events`: the processor
        // only ever saw the interface.
        void CollectTypes(JNIEnv* env, jclass clazz, std::vector<std::string>& names, int depth) {
            if (clazz == nullptr || depth > 8)
                return;

            const std::string name = ClassName(env, clazz);
            if (!name.empty()) {
                for (const std::string& seen : names) {
                    if (seen == name)
                        return;
                }
                names.push_back(name);
            }

            jclass parent = env->GetSuperclass(clazz);
            if (parent != nullptr) {
                CollectTypes(env, parent, names, depth + 1);
                env->DeleteLocalRef(parent);
            }

            jclass interfaces = env->GetObjectClass(clazz);
            if (interfaces == nullptr) {
                env->ExceptionClear();
                return;
            }
            jmethodID getInterfaces = env->GetMethodID(interfaces, "getInterfaces", "()[Ljava/lang/Class;");
            env->DeleteLocalRef(interfaces);
            if (getInterfaces == nullptr) {
                env->ExceptionClear();
                return;
            }

            auto list = static_cast<jobjectArray>(env->CallObjectMethod(clazz, getInterfaces));
            if (list == nullptr) {
                env->ExceptionClear();
                return;
            }
            const jsize count = env->GetArrayLength(list);
            for (jsize i = 0; i < count; i++) {
                auto item = static_cast<jclass>(env->GetObjectArrayElement(list, i));
                if (item != nullptr) {
                    CollectTypes(env, item, names, depth + 1);
                    env->DeleteLocalRef(item);
                }
            }
            env->DeleteLocalRef(list);
        }

        void BindSide(jni::Side side, JNIEnv* env, jobject instance) {
            if (instance == nullptr) {
                Log::Error("BUS", "bind was called without an instance");
                return;
            }

            jclass clazz = env->GetObjectClass(instance);
            if (clazz == nullptr) {
                env->ExceptionClear();
                Log::Error("BUS", "cannot resolve the class of the instance to bind");
                return;
            }

            std::vector<std::string> types;
            CollectTypes(env, clazz, types, 0);

            Handlers& current = HandlersOf(side);
            int bound = 0;
            for (const std::string& type : types) {
                bool alreadyBound = false;
                for (const std::string& seen : current.owners)
                    alreadyBound |= seen == type;

                // Every row this declaration type owns is served by the same instance, so the type is
                // recorded once and a second instance of it is refused rather than half applied.
                int matches = 0;
                for (int id = 0; id < Bus::KIND_COUNT; id++) {
                    const auto kind = static_cast<Bus::Kind>(id);
                    const Bus::CallEntry* call = Bus::FindCall(kind);
                    const Bus::DirectEntry* direct = Bus::FindDirect(kind);
                    const char* owner = call != nullptr ? call->owner_class
                                                        : (direct != nullptr ? direct->owner_class : nullptr);
                    // The row's own side decides where it lands: skipping a row the other side performs is
                    // what keeps a table from holding the wrong VM's references.
                    const jni::Side performer = call != nullptr ? call->target : direct->target;
                    if (owner == nullptr || type != owner || performer != side)
                        continue;
                    matches++;
                    if (alreadyBound)
                        continue;

                    const char* name = call != nullptr ? call->method_name : direct->method_name;
                    const char* descriptor = call != nullptr ? call->descriptor : direct->descriptor;

                    current.instances[id] = env->NewGlobalRef(instance);
                    current.methods[id] = env->GetMethodID(clazz, name, descriptor);
                    if (current.methods[id] == nullptr) {
                        env->ExceptionClear();
                        Log::InfoF("BUS", "%s has no %s%s", type.c_str(), name, descriptor);
                    }
                    bound++;
                }

                if (matches == 0)
                    continue;
                if (alreadyBound) {
                    Log::VerboseF("BUS", "already bound: %s", type.c_str());
                    continue;
                }
                current.owners.push_back(type);
                Log::VerboseF("BUS", "bound %s", type.c_str());
            }

            if (bound == 0 && current.owners.empty())
                Log::VerboseF("BUS", "bind: %s declares nothing to bind", ClassName(env, clazz).c_str());

            env->DeleteLocalRef(clazz);
        }

    } // namespace

    jobject BoundInstance(jni::Side side, Bus::Kind kind) {
        const int id = static_cast<int>(kind);
        if (id < 0 || id >= Bus::KIND_COUNT)
            return nullptr;
        return HandlersOf(side).instances[id];
    }

    jclass ResolveOwner(jni::Side side, const Bus::CallEntry& entry) {
        Handlers& current = HandlersOf(side);
        const int id = static_cast<int>(entry.kind);
        if (id < 0 || id >= Bus::KIND_COUNT)
            return nullptr;
        if (current.staticOwners[id] != nullptr)
            return current.staticOwners[id];

        jni::Env targetEnv(side);
        JNIEnv* env = targetEnv.Get();
        if (env == nullptr)
            return nullptr;

        std::string path = entry.owner_class;
        for (char& character : path) {
            if (character == '.')
                character = '/';
        }

        jclass found = env->FindClass(path.c_str());
        if (found == nullptr) {
            env->ExceptionClear();
            Log::InfoF("BUS", "cannot resolve the declaring class %s", entry.owner_class);
            return nullptr;
        }
        current.staticOwners[id] = static_cast<jclass>(env->NewGlobalRef(found));
        env->DeleteLocalRef(found);
        return current.staticOwners[id];
    }

    jmethodID MethodOf(jni::Side side, Bus::Kind kind) {
        const int id = static_cast<int>(kind);
        if (id < 0 || id >= Bus::KIND_COUNT)
            return nullptr;

        Handlers& current = HandlersOf(side);
        if (current.methods[id] != nullptr)
            return current.methods[id];

        const Bus::CallEntry* entry = Bus::FindCall(kind);
        if (entry == nullptr || !entry->is_static)
            return nullptr;

        // A static row is declared on a class that never binds, so its class is resolved from the table.
        jclass owner = ResolveOwner(side, *entry);
        if (owner == nullptr)
            return nullptr;

        jni::Env targetEnv(side);
        JNIEnv* env = targetEnv.Get();
        if (env == nullptr)
            return nullptr;

        current.methods[id] = env->GetStaticMethodID(owner, entry->method_name, entry->descriptor);
        if (current.methods[id] == nullptr) {
            env->ExceptionClear();
            Log::InfoF("BUS", "%s has no %s%s", entry->owner_class, entry->method_name, entry->descriptor);
        }
        return current.methods[id];
    }

    void BindArt(JNIEnv* env, jclass, jobject handlers) {
        // The ART side's entry, reached from `ArtBus.bind`: the object is walked against this side's rows
        // only, which is what keeps the other VM's references out of this table.
        BindSide(jni::Side::Art, env, handlers);
    }

    void BindJvm(JNIEnv* env, jclass, jobject handlers) {
        BindSide(jni::Side::Jvm, env, handlers);
    }

} // namespace copper::bridge::bus::Handlers
