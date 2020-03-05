DROP INDEX "things_exact_name_idx";

CREATE INDEX "things_exact_name_idx" ON "things" (lower("name"));