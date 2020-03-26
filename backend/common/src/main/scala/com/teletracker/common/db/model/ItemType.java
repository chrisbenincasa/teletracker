package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ItemType {
    Movie("movie"),
    Show("show"),
    Person("person");

    private final String name;

    ItemType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static ItemType fromString(final String s) {
        for (ItemType src : ItemType.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find ThingType with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
