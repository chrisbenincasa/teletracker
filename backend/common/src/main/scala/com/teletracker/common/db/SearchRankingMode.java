package com.teletracker.common.db;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SearchRankingMode {
    Popularity("popularity"),
    PopularityAndVotes("popularity_and_votes");

    private final String value;

    SearchRankingMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SearchRankingMode fromString(final String s) {
        for (SearchRankingMode genre : SearchRankingMode.values()) {
            if (genre.getValue().equalsIgnoreCase(s)) {
                return genre;
            }
        }

        throw new IllegalArgumentException("Could not find SearchRankingMode with name " + s);
    }

    @Override
    public String toString() {
        return value;
    }
}
