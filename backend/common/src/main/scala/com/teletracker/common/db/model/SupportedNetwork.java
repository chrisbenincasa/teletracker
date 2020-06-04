package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SupportedNetwork {
    Netflix(ExternalSource.Netflix.name(), 1),
    Hulu(ExternalSource.Hulu.name(), 2),
    Hbo("hbo", 3),
    HboMax(ExternalSource.HboMax.name(), 4),
    DisneyPlus(ExternalSource.DisneyPlus.name(), 5);

    private final int value;
    private final String name;

    SupportedNetwork(final String name, final int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static SupportedNetwork fromValue(final int i) {
        for (SupportedNetwork src : SupportedNetwork.values()) {
            if (src.value == i) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find SupportedNetwork with ordinal " + i);
    }

    @JsonCreator
    public static SupportedNetwork fromString(final String s) {
        for (SupportedNetwork src : SupportedNetwork.values()) {
            // Convert any HBO GO or HBO NOW specific strings to just HBO.
            if (s.equalsIgnoreCase("hbo-go") || s.equalsIgnoreCase("hbo-now")) {
                return Hbo;
            }

            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find SupportedNetwork with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
