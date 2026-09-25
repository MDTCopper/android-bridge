#ifndef COPPER_BRIDGE_FILE_H
#define COPPER_BRIDGE_FILE_H

#include <string>

namespace copper::bridge::util::File {

    // Path and file questions, and nothing else. They live together because more than one module asks them -
    // the JRE loader, the ELF reader, the hook - and a second copy of "where does the directory end" is not
    // worth having. Nothing here opens anything.

    // Whether a path is an existing regular file.
    bool IsRegular(const std::string& path);

    // Directory part of a path, without a trailing slash. Returns "." when there is no separator.
    std::string DirName(const std::string& path);

    // Last component of a path, with no separator. Returns the path itself when there is none.
    std::string BaseName(const std::string& path);

} // namespace copper::bridge::util::File

#endif // COPPER_BRIDGE_FILE_H
