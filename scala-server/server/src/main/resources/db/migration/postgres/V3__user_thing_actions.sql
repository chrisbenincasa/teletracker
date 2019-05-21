CREATE TABLE user_thing_actions
(
    "id"       SERIAL       NOT NULL PRIMARY KEY,
    "user_id"  INTEGER      NOT NULL REFERENCES "users" ON UPDATE NO ACTION ON DELETE NO ACTION,
    "thing_id" INTEGER      NOT NULL REFERENCES "things" ON UPDATE NO ACTION ON DELETE NO ACTION,
    "action"   VARCHAR(255) NOT NULL,
    "value"    DECIMAL
);

CREATE INDEX "user_thing_actions_by_thing" ON "user_thing_actions" ("thing_id", "action");
CREATE INDEX "user_thing_actions_by_user" ON "user_thing_actions" ("user_id", "action");
CREATE UNIQUE INDEX "user_thing_actions_by_user_thing_uniq" ON "user_thing_actions" ("user_id", "thing_id", "action");