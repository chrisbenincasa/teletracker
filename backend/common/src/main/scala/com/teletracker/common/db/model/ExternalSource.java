package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExternalSource {
    TheMovieDb("tmdb"),
    JustWatch("justwatch"),
    Imdb("imdb"),
    TvDb("tvdb"),
    Wikidata("wikidata"),
    Hulu("hulu"),
    Netflix("netflix"),
    Hbo("hbo");

    private final String name;

    ExternalSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static ExternalSource fromOrdinal(final int i) {
        for (ExternalSource src : ExternalSource.values()) {
            if (src.ordinal() == i) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find ExternalSource with ordinal " + i);
    }

    @JsonCreator
    public static ExternalSource fromString(final String s) {
        for (ExternalSource src : ExternalSource.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find ExternalSource with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
