ALTER TABLE "collection_things"
    ADD CONSTRAINT "collection_things_by_collection"
        UNIQUE USING INDEX "collection_things_by_collection";