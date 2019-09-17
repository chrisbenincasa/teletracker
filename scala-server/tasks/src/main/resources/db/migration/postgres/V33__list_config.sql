ALTER TABLE "lists" ADD COLUMN "options" jsonb;

ALTER TABLE "list_things" ADD COLUMN "removed_at" TIMESTAMP WITH time zone;