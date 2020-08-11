package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import javax.annotation.Nullable;

public enum SupportedNetwork {
    Netflix(ExternalSource.Netflix, null, 1),
    Hulu(ExternalSource.Hulu, null, 2),
    Hbo(ExternalSource.HboGo, "hbo", 3),
    HboMax(ExternalSource.HboMax, null, 4),
    DisneyPlus(ExternalSource.DisneyPlus, null, 5),
    AmazonPrimeVideo(ExternalSource.AmazonPrimeVideo, null, 6),
    AmazonVideo(ExternalSource.AmazonVideo, null, 7),
    AppleTv(ExternalSource.AppleTv, null, 8);

    private final int value;
    private final String name;
    private final ExternalSource externalSource;

    SupportedNetwork(final ExternalSource externalSource, @Nullable final String name, final int value) {
        this.externalSource = externalSource;
        this.name = name == null ? externalSource.getName() : name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public ExternalSource getExternalSource() { return externalSource; }

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
