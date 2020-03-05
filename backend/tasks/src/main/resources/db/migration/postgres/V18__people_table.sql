CREATE TABLE "people"
(
    "id"              UUID                     NOT NULL PRIMARY KEY,
    "name"            VARCHAR                  NOT NULL,
    "normalized_name" VARCHAR                  NOT NULL,
    "type"            VARCHAR                  NOT NULL,
    "created_at"      timestamp WITH time zone NOT NULL,
    "last_updated_at" timestamp WITH time zone NOT NULL,
    "metadata"        jsonb,
    "popularity"      DOUBLE PRECISION DEFAULT NULL,
    "tmdb_id"         VARCHAR DEFAULT NULL
);

CREATE INDEX ON "people" (popularity NULLS LAST);

ALTER TABLE "person_things" DROP CONSTRAINT "person_id_foreign";

ALTER TABLE "person_things"
    ADD CONSTRAINT "person_id_foreign" FOREIGN KEY ("person_id") REFERENCES "people" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

CREATE INDEX "person_name_full_text_idx" ON "people" USING GIN (to_tsvector('english', name));

CREATE INDEX "character_name_full_text_idx" ON "person_things" USING GIN (to_tsvector('english', character_name));