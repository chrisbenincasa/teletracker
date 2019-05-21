CREATE TABLE "dynamic_lists"
(
    "id"         SERIAL                NOT NULL PRIMARY KEY,
    "name"       VARCHAR               NOT NULL,
    "is_default" BOOLEAN DEFAULT FALSE NOT NULL,
    "is_public"  BOOLEAN DEFAULT TRUE  NOT NULL,
    "user_id"    INTEGER               NOT NULL,
    "rules"      JSONB                 NOT NULL
);

ALTER TABLE "dynamic_lists"
    ADD CONSTRAINT "dynamic_lists_user_id_fk" FOREIGN KEY ("user_id") REFERENCES "users" ("id") ON
        UPDATE
        NO ACTION ON DELETE NO ACTION;

CREATE INDEX "dynamic_lists_by_user" ON "dynamic_lists" ("user_id");

CREATE INDEX "lists_by_user" ON "lists" ("user_id");