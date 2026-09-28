#ifndef COPPER_BRIDGE_HOOK_H
#define COPPER_BRIDGE_HOOK_H

#include <string>

namespace copper::bridge::jre::Hook {

    // Taking over the VM's exit, its output streams and the log file's opens, with ByteHook. exit() runs the
    // destructors of every library loaded here, killing ART threads that touch that state. A symbol of ours is never
    // reached (bionic does not promote a loaded library to global scope, measured), so its GOT slot is patched.

    // PrepareHooks runs once, after the JRE's libraries: it only hooks what it saw when it started.
    void PrepareHooks();

    void InstallHook(const std::string& library);

} // namespace copper::bridge::jre::Hook

#endif // COPPER_BRIDGE_HOOK_H
