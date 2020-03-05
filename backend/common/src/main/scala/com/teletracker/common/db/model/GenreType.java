package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum GenreType {
    Movie("movie"),
    Tv("tv");

    private final String name;

    GenreType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static GenreType fromString(final String s) {
        for (GenreType genre : GenreType.values()) {
            if (genre.getName().equalsIgnoreCase(s)) {
                return genre;
            }
        }

        throw new IllegalArgumentException("Could not find GenreType with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}