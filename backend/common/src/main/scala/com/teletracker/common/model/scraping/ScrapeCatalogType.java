package com.teletracker.common.model.scraping;

public enum ScrapeCatalogType {
    HuluCatalog("HuluCatalog", ScrapeItemType.HuluItem),
    HboCatalog("HboCatalog", ScrapeItemType.HboItem),
    NetflixCatalog("NetflixCatalog", ScrapeItemType.NetflixItem),
    DisneyPlusCatalog("DisneyPlusCatalog", ScrapeItemType.DisneyPlusCatalogItem),
    HboMaxCatalog("HboMaxCatalog", ScrapeItemType.HboItem),
    HboChanges("HboChanges", ScrapeItemType.HboChangeItem),
    NetflixOriginalsArriving("NetflixOriginalsArriving", ScrapeItemType.NetflixOriginalsArriving),
    AmazonVideo("AmazonVideo", ScrapeItemType.AmazonItem);

    private final String type;
    private final ScrapeItemType scrapeItemType;

    ScrapeCatalogType(String type, ScrapeItemType scrapeItemType) {
        this.type = type;
        this.scrapeItemType = scrapeItemType;
    }

    @Override
    public String toString() {
        return type;
    }

    public static ScrapeCatalogType fromString(final String input) {
        for (ScrapeCatalogType scrapeCatalogType : values()) {
            if (scrapeCatalogType.type.equalsIgnoreCase(input)) {
                return scrapeCatalogType;
            }
        }

        throw new IllegalArgumentException("No ScrapeItemType for input string = " + input);
    }
}
