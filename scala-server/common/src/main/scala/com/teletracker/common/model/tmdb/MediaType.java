package com.teletracker.common.model.tmdb;

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
}
