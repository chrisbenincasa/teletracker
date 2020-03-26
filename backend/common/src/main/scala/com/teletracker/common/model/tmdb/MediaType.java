package com.teletracker.common.model.tmdb;

import com.teletracker.common.db.model.ItemType;

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

    public ItemType toThingType() {
        switch (this) {
            case Movie: return ItemType.Movie;
            case Tv: return ItemType.Show;
        }

        throw new IllegalArgumentException("");
    }
}
