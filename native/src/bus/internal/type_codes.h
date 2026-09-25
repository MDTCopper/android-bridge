#ifndef COPPER_BRIDGE_BUS_INTERNAL_TYPE_CODES_H
#define COPPER_BRIDGE_BUS_INTERNAL_TYPE_CODES_H

#include "gen/bus.h"

// What a type code means.
//
// A row describes its parameters and its return value as one character each, and those characters are the
// whole description of a payload: which of them travel by reference and how many jint slots the others
// take.

namespace copper::bridge::bus::TypeCodes {

    /** Whether a type code travels by reference rather than in a jint slot. */
    constexpr bool IsObject(char code) {
        return code == 'L' || code == 'l' || code == 'z' || code == 'b' || code == 'c' || code == 's'
                || code == 'i' || code == 'j' || code == 'f' || code == 'd';
    }

    /** How many of a row's parameters are objects, which is how many the message has to carry. */
    constexpr int ObjectCount(const char* codes) {
        int count = 0;
        for (int i = 0; codes[i] != '\0'; i++) {
            if (IsObject(codes[i]))
                count++;
        }
        return count;
    }

} // namespace copper::bridge::bus::TypeCodes

#endif // COPPER_BRIDGE_BUS_INTERNAL_TYPE_CODES_H
