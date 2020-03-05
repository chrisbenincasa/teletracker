DROP INDEX IF EXISTS "things_popularity";

CREATE INDEX IF NOT EXISTS "thing_popularity_id_sort_idx" ON "things" (popularity DESC NULLS LAST, id NULLS FIRST);

DROP INDEX IF EXISTS "thing_is_adult";

ALTER INDEX IF EXISTS "thing_is_adult_2" RENAME TO "thing_is_adult";