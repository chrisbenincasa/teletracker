CREATE TABLE "users"
(
    "id"              SERIAL    NOT NULL PRIMARY KEY,
    "name"            VARCHAR   NOT NULL,
    "username"        VARCHAR   NOT NULL UNIQUE,
    "email"           VARCHAR   NOT NULL UNIQUE,
    "password"        VARCHAR   NOT NULL,
    "created_at"      TIMESTAMP NOT NULL,
    "last_updated_at" TIMESTAMP NOT NULL
);

CREATE TABLE "user_credentials"
(
    "user_id"  INTEGER NOT NULL,
    "password" VARCHAR NOT NULL,
    "salt"     BYTEA   NOT NULL
);

CREATE TABLE "events"
(
    "id"                 SERIAL    NOT NULL PRIMARY KEY,
    "type"               VARCHAR   NOT NULL,
    "target_entity_type" VARCHAR   NOT NULL,
    "target_entity_id"   VARCHAR   NOT NULL,
    "details"            VARCHAR,
    "user_id"            INTEGER   NOT NULL,
    "timestamp"          TIMESTAMP NOT NULL
);

CREATE TABLE "genres"
(
    "id"   SERIAL  NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "type" VARCHAR NOT NULL,
    "slug" VARCHAR NOT NULL
);

CREATE TABLE "networks"
(
    "id"        SERIAL  NOT NULL PRIMARY KEY,
    "name"      VARCHAR NOT NULL,
    "slug"      VARCHAR NOT NULL UNIQUE,
    "shortname" VARCHAR NOT NULL,
    "homepage"  VARCHAR,
    "origin"    VARCHAR
);

CREATE TABLE "network_references"
(
    "id"              SERIAL  NOT NULL PRIMARY KEY,
    "external_source" VARCHAR NOT NULL,
    "external_id"     VARCHAR NOT NULL,
    "network_id"      INTEGER NOT NULL
);

CREATE TABLE "things"
(
    "id"              UUID                     NOT NULL PRIMARY KEY,
    "name"            VARCHAR                  NOT NULL,
    "normalized_name" VARCHAR                  NOT NULL,
    "type"            VARCHAR                  NOT NULL,
    "created_at"      timestamp WITH time zone NOT NULL,
    "last_updated_at" timestamp WITH time zone NOT NULL,
    "metadata"        jsonb
);

CREATE TABLE "thing_networks"
(
    "objects_id"  UUID NOT NULL,
    "networks_id" INTEGER NOT NULL
);

CREATE TABLE "lists"
(
    "id"         SERIAL                NOT NULL PRIMARY KEY,
    "name"       VARCHAR               NOT NULL,
    "is_default" BOOLEAN DEFAULT FALSE NOT NULL,
    "is_public"  BOOLEAN DEFAULT TRUE  NOT NULL,
    "user_id"    INTEGER               NOT NULL
);

CREATE TABLE "list_things"
(
    "lists_id"   INTEGER NOT NULL,
    "objects_id" UUID NOT NULL
);

CREATE TABLE "tv_show_episodes"
(
    "id"              SERIAL  NOT NULL PRIMARY KEY,
    "number"          INTEGER NOT NULL,
    "thing_id"        UUID    NOT NULL,
    "season_id"       INTEGER NOT NULL,
    "name"            VARCHAR NOT NULL,
    "production_code" VARCHAR
);

CREATE TABLE "tv_show_seasons"
(
    "id"       SERIAL  NOT NULL PRIMARY KEY,
    "number"   INTEGER NOT NULL,
    "show_id"  UUID NOT NULL,
    "overview" VARCHAR,
    "air_date" date
);

CREATE TABLE "availability"
(
    "id"                 SERIAL  NOT NULL PRIMARY KEY,
    "is_available"       BOOLEAN NOT NULL,
    "region"             VARCHAR,
    "num_seasons"        INTEGER,
    "start_date"         timestamptz,
    "end_date"           timestamptz,
    "offer_type"         VARCHAR,
    "cost"               DECIMAL(21, 2),
    "currency"           VARCHAR,
    "thing_id"           UUID,
    "tv_show_episode_id" INTEGER,
    "network_id"         INTEGER,
    "presentation_type"  VARCHAR
);

CREATE TABLE "external_ids"
(
    "id"              SERIAL NOT NULL PRIMARY KEY,
    "thing_id"        UUID,
    "tv_episode_id"   INTEGER,
    "tmdb_id"         VARCHAR,
    "imdb_id"         VARCHAR,
    "netflix_id"      VARCHAR,
    "last_updated_at" timestamp WITH time zone DEFAULT now(
        )                    NOT NULL
);

CREATE TABLE "genre_references"
(
    "id"              SERIAL  NOT NULL PRIMARY KEY,
    "external_source" VARCHAR NOT NULL,
    "external_id"     VARCHAR NOT NULL,
    "genre_id"        INTEGER NOT NULL
);

CREATE TABLE "thing_genres"
(
    "thing_id" UUID NOT NULL,
    "genre_id" INTEGER NOT NULL
);

CREATE TABLE "certifications"
(
    "id"            SERIAL  NOT NULL PRIMARY KEY,
    "type"          VARCHAR NOT NULL,
    "iso_3166_1"    VARCHAR NOT NULL,
    "certification" VARCHAR NOT NULL,
    "homepage"      VARCHAR NOT NULL,
    "order"         INTEGER NOT NULL
);

CREATE TABLE "person_things"
(
    "person_id"     UUID NOT NULL,
    "thing_id"      UUID NOT NULL,
    "relation_type" VARCHAR NOT NULL
);

CREATE TABLE "tokens"
(
    "id"              SERIAL      NOT NULL PRIMARY KEY,
    "user_id"         INTEGER     NOT NULL,
    "token"           text        NOT NULL,
    "created_at"      timestamptz NOT NULL,
    "last_updated_at" timestamptz NOT NULL,
    "revoked_at"      timestamptz
);

CREATE INDEX "unique_slug_type" ON "things" ("normalized_name", "type");

CREATE INDEX "availability_end_date_idx" ON "availability" ("end_date");

CREATE INDEX "availability_thing_id_networkid" ON "availability" ("thing_id", "network_id");

CREATE INDEX "user_id_idx" ON "tokens" ("user_id");

CREATE UNIQUE INDEX "network_references_source_id_network_unique" ON "network_references" ("external_source", "external_id", "network_id");

CREATE UNIQUE INDEX "genre_references_source_id_genre_unique" ON "genre_references" ("external_source", "external_id", "genre_id");

CREATE UNIQUE INDEX "uniq_cert_idx" ON "certifications" ("iso_3166_1", "type", "certification", "order");

ALTER TABLE "user_credentials"
    ADD CONSTRAINT "user_credentials_user_id" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON
        UPDATE
        RESTRICT ON DELETE CASCADE;

ALTER TABLE "events"
    ADD CONSTRAINT "events_user_fk" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "network_references"
    ADD CONSTRAINT "network_references_network_id_fk" FOREIGN KEY ("network_id") REFERENCES "networks" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "thing_networks"
    ADD CONSTRAINT "thing_networks_pk_thing_network" PRIMARY KEY ("objects_id", "networks_id");

ALTER TABLE "thing_networks"
    ADD CONSTRAINT "thing_networks_fk_networks" FOREIGN KEY ("networks_id") REFERENCES "networks" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "thing_networks"
    ADD CONSTRAINT "thing_networks_fk_things" FOREIGN KEY ("objects_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "lists"
    ADD CONSTRAINT "lists_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "list_things"
    ADD CONSTRAINT "list_id_thing_id" PRIMARY KEY ("lists_id", "objects_id");

ALTER TABLE "list_things"
    ADD CONSTRAINT "list_things_list_fk" FOREIGN KEY ("lists_id") REFERENCES "lists" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "list_things"
    ADD CONSTRAINT "list_things_thing_fk" FOREIGN KEY ("objects_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "tv_show_episodes"
    ADD CONSTRAINT "tv_show_episodes_show_fk" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "tv_show_seasons"
    ADD CONSTRAINT "tv_show_seasons_show_id_fk" FOREIGN KEY ("show_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "availability"
    ADD CONSTRAINT "availability_network_id_fk" FOREIGN KEY ("network_id") REFERENCES "networks" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "availability"
    ADD CONSTRAINT "availability_thing_id_fk" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "availability"
    ADD CONSTRAINT "availability_tv_show_episode_id_fk" FOREIGN KEY ("tv_show_episode_id") REFERENCES "tv_show_episodes" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "external_ids"
    ADD CONSTRAINT "external_ids_episodes_fk" FOREIGN KEY ("tv_episode_id") REFERENCES "tv_show_episodes" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "external_ids"
    ADD CONSTRAINT "external_ids_thing_fk" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "external_ids"
    ADD CONSTRAINT "external_ids_thing_raw_fk" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "genre_references"
    ADD CONSTRAINT "network_references_network_id_fk" FOREIGN KEY ("genre_id") REFERENCES "genres" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "thing_genres"
    ADD CONSTRAINT "thing_genres_pk_thing_network" PRIMARY KEY ("thing_id", "genre_id");

ALTER TABLE "thing_genres"
    ADD CONSTRAINT "thing_genres_fk_genre" FOREIGN KEY ("genre_id") REFERENCES "genres" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "thing_genres"
    ADD CONSTRAINT "thing_genres_fk_things" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "person_things"
    ADD CONSTRAINT "person_thing_prim_key" PRIMARY KEY ("person_id", "thing_id");

ALTER TABLE "person_things"
    ADD CONSTRAINT "person_id_foreign" FOREIGN KEY ("person_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "person_things"
    ADD CONSTRAINT "thing_id_foreign" FOREIGN KEY ("thing_id") REFERENCES "things" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

ALTER TABLE "tokens"
    ADD CONSTRAINT "user_fk" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;