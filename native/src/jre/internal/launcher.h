#ifndef COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
#define COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H

namespace copper::bridge::jre::Launcher {

    // Ending what the VM left behind: hook.cpp calls it when the VM's exit is caught, the launch when JLI_Launch
    // returns.

    // A failure (non-zero) is unrecoverable: the process ends at once, with that status and no teardown - the
    // libraries' destructors kill ART threads touching that state (measured). A clean return hands the screen to ART
    // and parks this thread.
    [[noreturn]] void EndProcessNow(int status);

} // namespace copper::bridge::jre::Launcher

#endif // COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
