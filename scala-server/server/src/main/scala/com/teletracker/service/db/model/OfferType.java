package com.teletracker.service.db.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum OfferType {
    Buy("buy"),
    Rent("rent"),
    Theater("theater"),
    Subscription("subscription"),
    Free("free"),
    Ads("ads"),
    Aggregate("aggregate");

    private final String name;

    OfferType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @JsonCreator
    public static OfferType fromString(final String s) {
        for (OfferType src : OfferType.values()) {
            if (src.getName().equalsIgnoreCase(s)) {
                return src;
            }
        }

        throw new IllegalArgumentException("Could not find OfferType with name " + s);
    }

    public static OfferType fromJustWatchType(final String s) {
        switch (s.toLowerCase()) {
            case "buy":
                return OfferType.Buy;
            case "rent":
                return OfferType.Rent;
            case "theater":
                return OfferType.Theater;
            case "cinema":
                return OfferType.Theater;
            case "subscription":
                return OfferType.Subscription;
            case "flatrate":
                return OfferType.Subscription;
            case "free":
                return OfferType.Free;
            case "ads":
                return OfferType.Ads;
            case "aggregate":
                return OfferType.Aggregate;
            default:
                throw new IllegalArgumentException("Could not find OfferType with name " + s);
        }
    }

    @Override
    public String toString() {
        return this.name;
    }
}
