package com.teletracker.service.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ThingType {
    Movie("movie"),
    Show("show"),
    Person("person");

    private final String name;

    ThingType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static ThingType fromString(final String s) {
        for (ThingType src : ThingType.values()) {
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
