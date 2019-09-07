CREATE UNIQUE INDEX external_ids_thing_id_uniq ON "external_ids" ("thing_id");
CREATE UNIQUE INDEX external_ids_tv_episode_id_uniq ON "external_ids" ("tv_episode_id");

ALTER TABLE "external_ids" ADD CONSTRAINT entity_id_xor
    CHECK (("thing_id" IS NOT NULL AND "tv_episode_id" IS NULL) OR ("thing_id" IS NULL AND "tv_episode_id" IS NOT NULL));