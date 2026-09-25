#include "util/abi.h"

namespace copper::bridge::util::Abi {

    int CodeFromName(const std::string& abi) {
        if (abi == "arm64-v8a")
            return 1;
        if (abi == "armeabi-v7a")
            return 2;
        if (abi == "x86_64")
            return 3;
        return 0;
    }

    int CompiledCode() {
#if defined(__aarch64__)
        return 1;
#elif defined(__arm__)
        return 2;
#elif defined(__x86_64__)
        return 3;
#else
        return 0;
#endif
    }

} // namespace copper::bridge
