# Builds libcopperbridge.so: the JNI half of the bridge.
#
# This library runs in two VMs of the same process: ART loads it to create the surface and start the JVM,
# and the JVM that JLI_Launch creates loads it again to reach back into ART. JNI_OnLoad decides which side
# it is by trying to bind each side's classes.
#
# Everything compiles straight into the shared object, so the layering under src/ is a convention the
# sources keep rather than something the build enforces; what the build enforces is the include root,
# src/ alone. The ABI and the API level must match the bridge jar: minSdk 30, and the same NDK.

cmake_minimum_required(VERSION 3.22)
project(copperbridge C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The compile options are about footprint, because this library is loaded into every game process and
# shipped inside bridge.jar:
#
#   -fno-exceptions -fno-rtti  nothing here throws, catches, dynamic_casts or asks for a typeid, and
#                              dropping them takes the whole libc++ exception and RTTI machinery with it
#   -Oz                        size over speed: this is not hot code
#   -ffunction-sections -fdata-sections   so the linker can drop every unreached function and constant
#
# Enumerated, not listed: CONFIGURE_DEPENDS re-runs the glob when the tree changes, and a directory
# that stops matching is then a link error naming the symbol rather than a list someone forgot.
file(GLOB_RECURSE COPPER_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)

add_library(copperbridge SHARED ${COPPER_SOURCES})

set_target_properties(copperbridge PROPERTIES CXX_VISIBILITY_PRESET hidden)
target_compile_options(copperbridge PRIVATE
        -Wall -Wextra
        -fno-exceptions -fno-rtti
        -Oz
        -ffunction-sections -fdata-sections)

# The source root, and nothing else: every include in this library is written from here.
target_include_directories(copperbridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)

# Keep nothing the code does not reach, fold identical code, keep the statically linked C++ runtime
# out of the export table, and strip. The last one matters most: the toolchain compiles with -g in
# every configuration, so without it the symbol table and the debug sections are around 85% of the
# file (measured).
#
# --exclude-libs,ALL matters because the NDK links libc++ as an archive (-static-libstdc++), and
# without it its exported symbols are about a third of the library. Stripping only affects what is
# shipped, so relinking without -s still produces a symbolized library for a crash report.
target_link_options(copperbridge PRIVATE
        -Wl,--gc-sections
        -Wl,--icf=all
        -Wl,--exclude-libs,ALL
        -Wl,-s)

# android: ANativeWindow_fromSurface, in the binding layer.
# dl:      dlopen/dlsym, which the JRE loader is built around.
# log:     __android_log_print, the one thing that puts a line in Android's log.
target_link_libraries(copperbridge PRIVATE android dl log)

# ByteHook (MIT): the VM's exit has to be taken over before it tears this process down, and that needs
# the GOT slot of the calling library patched, not a symbol of ours - src/jre/hook.cpp has why.
#
# Fetched, not vendored, and compiled here rather than by its own CMake: its CMakeLists would produce a
# second shared library. shadowhook is not built either - it cannot initialize on this ROM - and the patch
# beside this file takes out the dl-monitor it would be registered with. The patch resets the tree first so
# it applies cleanly when the fetch step repeats; the tag is pinned, which is what makes it stable.
include(FetchContent)
FetchContent_Declare(bytehook
        GIT_REPOSITORY https://github.com/bytedance/bhook.git
        GIT_TAG v1.1.2
        GIT_SHALLOW TRUE
        PATCH_COMMAND git checkout -- .
        COMMAND git apply --whitespace=nowarn ${CMAKE_CURRENT_SOURCE_DIR}/third_party/bhook/no-dl-monitor.patch)
FetchContent_Populate(bytehook)

set(BHOOK_ROOT ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp)
file(GLOB BHOOK_SOURCES CONFIGURE_DEPENDS ${BHOOK_ROOT}/*.c)
target_sources(copperbridge PRIVATE ${BHOOK_SOURCES})
target_include_directories(copperbridge PRIVATE
        ${BHOOK_ROOT}
        ${BHOOK_ROOT}/include
        ${BHOOK_ROOT}/third_party/bsd
        ${BHOOK_ROOT}/third_party/lss)

# The page macros its sources expect and this NDK does not define (16 KB: the largest page Android runs
# with, and still aligned on a 4 KB device), and a rename of the JNI_OnLoad it ships, because the VM can
# only look up one and this library already has one.
set_source_files_properties(${BHOOK_SOURCES} PROPERTIES
        COMPILE_DEFINITIONS "PAGE_SIZE=16384;PAGE_MASK=~(16384-1);JNI_OnLoad=copper_bhook_jni_on_load")
