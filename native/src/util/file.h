#ifndef COPPER_BRIDGE_FILE_H
#define COPPER_BRIDGE_FILE_H

#include <string>

namespace copper::bridge::util::File {

    // Path and file questions, and nothing else: more than one module asks them, and a second copy is not worth having.

    bool IsRegular(const std::string& path);

    // Directory part of a path, without a trailing slash. Returns "." when there is no separator.
    std::string DirName(const std::string& path);

    // Last component of a path, with no separator. Returns the path itself when there is none.
    std::string BaseName(const std::string& path);

} // namespace copper::bridge::util::File

#endif // COPPER_BRIDGE_FILE_H
