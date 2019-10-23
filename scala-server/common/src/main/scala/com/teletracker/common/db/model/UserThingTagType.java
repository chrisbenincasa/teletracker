package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UserThingTagType {
    Watched("watched", false),
    Enjoyed("enjoyed", true),
    TrackedInList("tracked_in_list", true);

    private final String name;
    private final boolean requiresValue;

    UserThingTagType(String name, boolean requiresValue) {
        this.name = name;
        this.requiresValue = requiresValue;
    }

    public String getName() {
        return name;
    }

    public boolean typeRequiresValue() {
        return this.requiresValue;
    }

    @JsonCreator
    public static UserThingTagType fromString(final String s) {
        for (UserThingTagType src : UserThingTagType.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find UserThingTagType with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
