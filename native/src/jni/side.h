#ifndef COPPER_BRIDGE_SIDE_H
#define COPPER_BRIDGE_SIDE_H

#include <cstdint>

// The vocabulary the two generated systems share, and the only thing they share.
//
// The bus tables and the batch tables are generated from independent declarations by independent
// processors, so neither may include the other's header; what they have in common is which virtual machine
// a row belongs to. Both generated headers include this file rather than each other.

namespace copper::bridge::jni {

/** Which virtual machine a row's handler lives on. */
enum class Side : int8_t { Art, Jvm };

} // namespace copper::bridge::jni

#endif // COPPER_BRIDGE_SIDE_H
