#include "util/file.h"

#include <sys/stat.h>

namespace copper::bridge::util::File {

    bool IsRegular(const std::string& path) {
        struct stat info;
        if (stat(path.c_str(), &info) != 0)
            return false;
        return S_ISREG(info.st_mode);
    }

    std::string DirName(const std::string& path) {
        const size_t slash = path.find_last_of('/');
        if (slash == std::string::npos)
            return ".";
        if (slash == 0)
            return "/";
        return path.substr(0, slash);
    }

    std::string BaseName(const std::string& path) {
        const size_t slash = path.find_last_of('/');
        return slash == std::string::npos ? path : path.substr(slash + 1);
    }

    } // namespace copper::bridge