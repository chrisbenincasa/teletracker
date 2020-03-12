package com.teletracker.common.tasks.model;

import java.util.Optional;

public enum TeletrackerTaskIdentifier {
    DENORMALIZE_ITEM_TASK("DenormalizeItemTask");

    private String value;

    TeletrackerTaskIdentifier(String value) {
        this.value = value;
    }

    public String identifier() {
        return value;
    }

    public static Optional<TeletrackerTaskIdentifier> forString(String target) {
        for (TeletrackerTaskIdentifier id : values()) {
            if (id.value.equals(target)) {
                return Optional.of(id);
            }
        }

        return Optional.empty();
    }
}
