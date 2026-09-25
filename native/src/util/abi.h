#ifndef COPPER_BRIDGE_ABI_H
#define COPPER_BRIDGE_ABI_H

#include <string>

namespace copper::bridge::util::Abi {

    // Android ABI names, and the codes Init() compares them with. The numbers are arbitrary but stable: they
    // only have to agree between the Java side and this code, and a mismatch is reported as a value rather
    // than as a crash once a load is already under way.

    // The code for one Android ABI name, or 0 when the name is not one this bridge knows.
    int CodeFromName(const std::string& abi);

    // The ABI this library was compiled for.
    int CompiledCode();

} // namespace copper::bridge::util::Abi

#endif // COPPER_BRIDGE_ABI_H
