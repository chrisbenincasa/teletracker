ALTER TABLE "things" ADD COLUMN "tmdb_id" VARCHAR;

CREATE UNIQUE INDEX "tmdb_external_id" ON "things" ("tmdb_id");

ALTER TABLE "things"
    ADD CONSTRAINT "unique_tmdb_external_id"
        UNIQUE USING INDEX tmdb_external_id;

-- CREATE UNIQUE INDEX "external_ids_thing_imdb" ON external_ids (thing_id, imdb_id);
--
-- ALTER TABLE "external_ids"
--     ADD CONSTRAINT unique_external_ids_thing_imdb
--         UNIQUE USING INDEX external_ids_thing_imdb;
--
-- CREATE UNIQUE INDEX "external_ids_thing_netflix" ON external_ids (thing_id, netflix_id);
--
-- ALTER TABLE "external_ids"
--     ADD CONSTRAINT unique_external_ids_thing_netflix
--         UNIQUE USING INDEX external_ids_thing_netflix;
