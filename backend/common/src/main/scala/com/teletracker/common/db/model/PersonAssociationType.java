package com.teletracker.common.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PersonAssociationType {
    Cast("cast"),
    Crew("crew");

    private final String type;

    PersonAssociationType(String type) {
        this.type = type;
    }

    @JsonCreator
    public static PersonAssociationType fromString(final String s) {
        for (PersonAssociationType src : PersonAssociationType.values()) {
            if (src.getType().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find PersonAssociationType with name " + s);
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return type;
    }
}
