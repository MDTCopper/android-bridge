#ifndef COPPER_BRIDGE_BUS_INTERNAL_ROW_H
#define COPPER_BRIDGE_BUS_INTERNAL_ROW_H

#include "gen/bus.h"

// What a row's kind says about the call it declares: a row that returns something has a caller blocked on the
// answer, and a row that returns nothing is performed later, so its message carries a box instead of references.

namespace copper::bridge::bus::Row {

    /** Whether the caller of this row waits for its answer. Not constexpr: the return code is data. */
    inline bool IsSync(gen::Bus::Kind kind) {
        const gen::Bus::CallEntry* entry = gen::Bus::FindCall(kind);
        return entry != nullptr && entry->return_code != 'V';
    }

    /** Whether a message of this kind carries a box rather than global references. */
    inline bool IsBoxed(gen::Bus::Kind kind) {
        const gen::Bus::CallEntry* entry = gen::Bus::FindCall(kind);
        return entry != nullptr && entry->return_code == 'V';
    }

} // namespace copper::bridge::bus::Row

#endif // COPPER_BRIDGE_BUS_INTERNAL_ROW_H
