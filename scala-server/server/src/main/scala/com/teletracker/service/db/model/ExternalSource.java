package com.teletracker.service.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExternalSource {
    TheMovieDb("tmdb"),
    JustWatch("justwatch");

    private final String name;

    ExternalSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static ExternalSource fromString(final String s) {
        for (ExternalSource src : ExternalSource.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find CertificationType with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
