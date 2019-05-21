CREATE TABLE user_network_preferences
(
    "id"         SERIAL  NOT NULL PRIMARY KEY,
    "user_id"    INTEGER NOT NULL REFERENCES "users" ON UPDATE NO ACTION ON DELETE NO ACTION,
    "network_id" INTEGER NOT NULL REFERENCES "networks" ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE UNIQUE INDEX "user_network_preferences_uniq" ON "user_network_preferences" ("user_id", "network_id");
CREATE INDEX "user_network_preferences_by_network" ON "user_network_preferences" ("network_id");

ALTER TABLE "users"
    ADD COLUMN "preferences" JSONB;