package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExternalSource {
    TheMovieDb("tmdb", 0),
    JustWatch("justwatch", 1),
    Imdb("imdb", 2),
    TvDb("tvdb", 3),
    Wikidata("wikidata", 4),
    Hulu("hulu", 5),
    Netflix("netflix", 6),
    HboGo("hbo-go", 7),
    HboNow("hbo-now", 8),
    DisneyPlus("disney-plus", 9);

    private final int value;
    private final String name;

    ExternalSource(final String name, final int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static ExternalSource fromValue(final int i) {
        for (ExternalSource src : ExternalSource.values()) {
            if (src.value == i) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find ExternalSource with ordinal " + i);
    }

    @JsonCreator
    public static ExternalSource fromString(final String s) {
        // Temporary read-repair
        if (s.equalsIgnoreCase("hbo")) {
            return HboGo;
        }

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
