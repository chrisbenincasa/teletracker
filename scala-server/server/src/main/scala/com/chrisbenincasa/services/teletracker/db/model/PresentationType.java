package com.chrisbenincasa.services.teletracker.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PresentationType {
    SD("sd"),
    HD("hd"),
    FourK("4k");

    private final String name;

    PresentationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static PresentationType fromString(final String s) {
        for (PresentationType src : PresentationType.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find OfferType with name " + s);
    }

    public static PresentationType fromJustWatchType(final String s) {
        switch (s.toLowerCase()) {
            case "sd": return PresentationType.SD;
            case "hd": return PresentationType.HD;
            case "4k": return PresentationType.FourK;
            default: throw new IllegalArgumentException("Could not find PresentationType with name " + s);
        }
    }

    @Override
    public String toString() {
        return this.name;
    }
}
