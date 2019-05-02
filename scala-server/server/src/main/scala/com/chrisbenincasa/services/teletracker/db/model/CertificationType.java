package com.chrisbenincasa.services.teletracker.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CertificationType {
    Movie("movie"),
    Tv("tv");

    private final String name;

    CertificationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static CertificationType fromString(final String s) {
        for (CertificationType certificationType : CertificationType.values()) {
            if (certificationType.getName().equalsIgnoreCase(s)) {
                return certificationType;
            }
        }

        throw new IllegalArgumentException("Could not find CertificationType with name " + s);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
