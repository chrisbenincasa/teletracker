package com.teletracker.common.model.scraping;

public enum ScrapeItemType {
    HuluCatalog("HuluCatalog"),
    HboCatalog("HboCatalog"),
    NetflixCatalog("NetflixCatalog"),
    DisneyPlusCatalog("DisneyPlusCatalog"),
    HboMaxCatalog("HboMaxCatalog"),
    HboChanges("HboChanges"),
    NetflixOriginalsArriving("NetflixOriginalsArriving");

    private final String type;

    ScrapeItemType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }

    public static ScrapeItemType fromString(final String input) {
        for (ScrapeItemType scrapeItemType : values()) {
            if (scrapeItemType.type.equalsIgnoreCase(input)) {
                return scrapeItemType;
            }
        }

        throw new IllegalArgumentException("No ScrapeItemType for input string = " + input);
    }
}
