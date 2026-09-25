#ifndef COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
#define COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H

namespace copper::bridge::jre::Launcher {

    // Ending what the VM left behind, declared here rather than in the module's public face: hook.cpp calls
    // it when the VM's own exit is caught and the launch calls it when JLI_Launch returns, and no generated
    // table takes its address.

    // Hands the ending to the screen first, then lets the VM's own exit run.
    //
    // <p>Two runtimes share this process and only one of them is going away: the VM's exit runs the
    // destructors of every library loaded into the process, the platform's included, and an ART-side thread
    // that touches any of that state afterwards dies of FORTIFY and SIGABRT (measured on the tablet, on the
    // OEM insets thread, while the game screen was still up). So the screen goes first, with its own
    // transition, and the VM's exit is then let through for real.</p>
    [[noreturn]] void EndProcessNow(int status);

} // namespace copper::bridge::jre::Launcher

#endif // COPPER_BRIDGE_JRE_INTERNAL_LAUNCHER_H
