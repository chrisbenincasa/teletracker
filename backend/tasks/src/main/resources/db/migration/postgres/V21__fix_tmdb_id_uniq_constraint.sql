ALTER TABLE "things"
    DROP CONSTRAINT "unique_tmdb_external_id";

-- DROP INDEX "tmdb_external_id";

CREATE UNIQUE INDEX "unique_tmdb_external_id" ON "things" ("type", "tmdb_id");

ALTER TABLE "things"
    ADD CONSTRAINT "unique_tmdb_external_id"
        UNIQUE USING INDEX unique_tmdb_external_id;