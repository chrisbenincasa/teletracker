-- Drop dynamic lists table
DROP INDEX "dynamic_lists_by_user";
TRUNCATE TABLE "dynamic_lists";
DROP TABLE "dynamic_lists";

-- Add dynamic columns to regular list table
ALTER TABLE "lists"
    ADD COLUMN "is_dynamic" BOOLEAN DEFAULT FALSE;
ALTER TABLE "lists"
    ADD COLUMN "rules" JSONB;

DROP INDEX "lists_by_user";
CREATE INDEX "lists_by_user" ON "lists" ("user_id", "is_dynamic");

ALTER TABLE "lists"
    ADD CONSTRAINT dynamic_lists_must_have_rules
        CHECK ((NOT "is_dynamic") OR ("rules" IS NOT NULL));