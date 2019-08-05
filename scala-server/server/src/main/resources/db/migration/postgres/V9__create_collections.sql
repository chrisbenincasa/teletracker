CREATE TABLE collections (
    "id" SERIAL NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "overview" TEXT DEFAULT NULL,
    "tmdb_id" VARCHAR DEFAULT NULL
);

CREATE UNIQUE INDEX "collections_by_tmdb_id" ON "collections"("tmdb_id");

CREATE TABLE collection_things (
    "id" SERIAL NOT NULL PRIMARY KEY,
    "collection_id" INTEGER REFERENCES "collections"("id") ON DELETE RESTRICT,
    "thing_id" UUID REFERENCES "things"("id") ON DELETE RESTRICT
);

CREATE UNIQUE INDEX "collection_things_by_collection" ON "collection_things" ("collection_id", "thing_id");
CREATE INDEX "collection_things_by_thing" ON "collection_things" ("thing_id");