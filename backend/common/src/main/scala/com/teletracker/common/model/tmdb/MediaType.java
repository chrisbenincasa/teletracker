package com.teletracker.common.model.tmdb;

import com.teletracker.common.db.model.ThingType;

public enum MediaType {
    Movie("movie"),
    Tv("tv");

    private final String type;

    MediaType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }

    public ThingType toThingType() {
        switch (this) {
            case Movie: return ThingType.Movie;
            case Tv: return ThingType.Show;
        }

        throw new IllegalArgumentException("");
    }
}
