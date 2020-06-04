package com.teletracker.common.elasticsearch.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EsPotentialMatchState {
    Unmatched("unmatched"),
    Matched("matched"),
    NonMatch("nonmatch");

    private final String name;

    EsPotentialMatchState(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static EsPotentialMatchState fromString(final String s) {
        for (EsPotentialMatchState certificationType : EsPotentialMatchState.values()) {
            if (certificationType.getName().equalsIgnoreCase(s)) {
                return certificationType;
            }
        }

        throw new IllegalArgumentException("Could not find EsPotentialMatchState with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
