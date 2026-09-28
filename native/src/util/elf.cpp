#include "util/elf.h"

#include <elf.h>

#include <cstdio>
#include <cstring>
#include <vector>

namespace copper::bridge::util::Elf {

    namespace {

        // One PT_LOAD segment, used to turn a virtual address from the dynamic section into a file offset.
        struct LoadSegment {
            uint64_t vaddr;
            uint64_t offset;
            uint64_t size;
        };

        bool VaddrToOffset(const std::vector<LoadSegment>& segments, uint64_t vaddr, uint64_t& offset) {
            for (const LoadSegment& segment : segments) {
                if (vaddr >= segment.vaddr && vaddr < segment.vaddr + segment.size) {
                    offset = segment.offset + (vaddr - segment.vaddr);
                    return true;
                }
            }
            return false;
        }

    } // namespace

    std::string ExpandOrigin(const std::string& entry, const std::string& origin) {
        std::string result;
        result.reserve(entry.size());

        for (size_t i = 0; i < entry.size();) {
            if (entry.compare(i, 7, "$ORIGIN") == 0) {
                result += origin;
                i += 7;
            } else if (entry.compare(i, 9, "${ORIGIN}") == 0) {
                result += origin;
                i += 9;
            } else {
                result += entry[i];
                i++;
            }
        }
        return result;
    }

    bool ReadDeps(const std::string& path, Deps& out, std::string& error) {
        out = Deps();
        out.path = path;

        FILE* file = fopen(path.c_str(), "rb");
        if (file == nullptr) {
            error = "cannot open " + path;
            return false;
        }

        Elf64_Ehdr header;
        if (fread(&header, sizeof(header), 1, file) != 1) {
            error = "short read on ELF header of " + path;
            fclose(file);
            return false;
        }
        if (memcmp(header.e_ident, ELFMAG, SELFMAG) != 0) {
            error = "not an ELF file: " + path;
            fclose(file);
            return false;
        }
        if (header.e_ident[EI_CLASS] != ELFCLASS64 || header.e_ident[EI_DATA] != ELFDATA2LSB) {
            error = "unsupported ELF class or endianness: " + path;
            fclose(file);
            return false;
        }
        if (header.e_phnum == 0) {
            // A shared object with no program headers has no dynamic section either: no dependencies, not a failure.
            fclose(file);
            return true;
        }

        std::vector<Elf64_Phdr> programHeaders(header.e_phnum);
        if (fseek(file, static_cast<long>(header.e_phoff), SEEK_SET) != 0
    || fread(programHeaders.data(), sizeof(Elf64_Phdr), header.e_phnum, file) != header.e_phnum) {
                error = "cannot read program headers of " + path;
            fclose(file);
            return false;
        }

        uint64_t dynamicOffset = 0;
        uint64_t dynamicSize = 0;
        std::vector<LoadSegment> segments;
        for (const Elf64_Phdr& phdr : programHeaders) {
            if (phdr.p_type == PT_DYNAMIC) {
                dynamicOffset = phdr.p_offset;
                dynamicSize = phdr.p_filesz;
            } else if (phdr.p_type == PT_LOAD) {
                segments.push_back(LoadSegment{phdr.p_vaddr, phdr.p_offset, phdr.p_filesz});
            }
        }

        if (dynamicOffset == 0 || dynamicSize == 0) {
            // Statically linked or stripped of its dynamic section: nothing to resolve.
            fclose(file);
            return true;
        }

        std::vector<Elf64_Dyn> dynamics(dynamicSize / sizeof(Elf64_Dyn));
        if (fseek(file, static_cast<long>(dynamicOffset), SEEK_SET) != 0
    || fread(dynamics.data(), sizeof(Elf64_Dyn), dynamics.size(), file) != dynamics.size()) {
                error = "cannot read the dynamic section of " + path;
            fclose(file);
            return false;
        }

        uint64_t stringTableVaddr = 0;
        uint64_t stringTableSize = 0;
        for (const Elf64_Dyn& entry : dynamics) {
            if (entry.d_tag == DT_STRTAB)
                stringTableVaddr = entry.d_un.d_ptr;
            else if (entry.d_tag == DT_STRSZ)
                stringTableSize = entry.d_un.d_val;
        }
        if (stringTableVaddr == 0 || stringTableSize == 0) {
            error = "no string table in the dynamic section of " + path;
            fclose(file);
            return false;
        }

        uint64_t stringTableOffset = 0;
        if (!VaddrToOffset(segments, stringTableVaddr, stringTableOffset)) {
            error = "string table of " + path + " is outside every PT_LOAD segment";
            fclose(file);
            return false;
        }

        std::vector<char> strings(stringTableSize);
        if (fseek(file, static_cast<long>(stringTableOffset), SEEK_SET) != 0
    || fread(strings.data(), 1, strings.size(), file) != strings.size()) {
                error = "cannot read the string table of " + path;
            fclose(file);
            return false;
        }

        // The string table is not guaranteed to end with a terminator, so bound every lookup.
        auto stringAt = [&strings](uint64_t offset) -> std::string {
            if (offset >= strings.size())
                return std::string();
            const char* start = strings.data() + offset;
            size_t limit = strings.size() - static_cast<size_t>(offset);
            size_t length = strnlen(start, limit);
            return std::string(start, length);
        };

        for (const Elf64_Dyn& entry : dynamics) {
            switch (entry.d_tag) {
                case DT_NEEDED:
                out.needed.push_back(stringAt(entry.d_un.d_val));
                break;
                case DT_SONAME:
                out.soname = stringAt(entry.d_un.d_val);
                break;
                case DT_RUNPATH:
                case DT_RPATH: {
                    // DT_RUNPATH wins over DT_RPATH, so runpath entries are prepended to stay ahead of inherited rpath.
                    std::string value = stringAt(entry.d_un.d_val);
                    size_t start = 0;
                    while (start <= value.size()) {
                        size_t colon = value.find(':', start);
                        std::string piece = value.substr(start,
                                 colon == std::string::npos ? std::string::npos : colon - start);
                        if (!piece.empty()) {
                            if (entry.d_tag == DT_RUNPATH)
                                out.searchPaths.insert(out.searchPaths.begin(), piece);
                            else
                                out.searchPaths.push_back(piece);
                        }
                        if (colon == std::string::npos)
                            break;
                        start = colon + 1;
                    }
                    break;
                }
                default:
                break;
            }
        }

        fclose(file);
        return true;
    }

} // namespace copper::bridge::util::Elf