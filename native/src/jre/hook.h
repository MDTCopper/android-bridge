#ifndef COPPER_BRIDGE_HOOK_H
#define COPPER_BRIDGE_HOOK_H

#include <string>

namespace copper::bridge::jre::Hook {

    // Taking over the VM's exit, its output streams, and the opens that would write the log file, with
    // ByteHook.
    //
    // The VM's exit() runs the destructors of every library loaded here, the platform's included, and an
    // ART-side thread that touches that state afterwards dies of FORTIFY and SIGABRT. A symbol of ours would
    // never be reached - bionic does not promote an already loaded library to the global scope (measured) -
    // so the GOT slot of the libraries that call it is patched instead, which also keeps ART's own exits out.

    // Initializes the hook library, once, after the JRE's libraries are loaded: it can only hook the
    // libraries it saw when it started (measured: hooking libjvm right after loading it, with the init done
    // on libjli a moment earlier, installed nothing). A missing hook library is a log line, not a failure.
    void PrepareHooks();

    // Takes the VM's exit, its output streams, and the opens of the log file over in one of the JRE's
    // libraries, named by its path relative to the JRE's lib directory: "libjli.so", "server/libjvm.so".
    // One library per call, after PrepareHooks.
    void InstallHook(const std::string& library);

} // namespace copper::bridge::jre::Hook

#endif // COPPER_BRIDGE_HOOK_H
