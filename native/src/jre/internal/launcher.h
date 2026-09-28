#ifndef COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
#define COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H

namespace copper::bridge::jre::Launcher {

    // Ending what the VM left behind: hook.cpp calls it when the VM's exit is caught, the launch when JLI_Launch
    // returns.

    // The screen goes first, then the VM's exit runs; its destructors kill ART threads touching that state (measured).
    [[noreturn]] void EndProcessNow(int status);

} // namespace copper::bridge::jre::Launcher

#endif // COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
