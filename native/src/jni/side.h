#ifndef COPPER_BRIDGE_SIDE_H
#define COPPER_BRIDGE_SIDE_H

#include <cstdint>

// The one thing the two generated systems share: which virtual machine a row belongs to. Neither includes the
// other's header.

namespace copper::bridge::jni {

/** Which virtual machine a row's handler lives on. */
enum class Side : int8_t { Art, Jvm };

} // namespace copper::bridge::jni

#endif // COPPER_BRIDGE_SIDE_H
