DROP INDEX "things_popularity";

CREATE INDEX "things_popularity" ON "things" (popularity DESC NULLS LAST);