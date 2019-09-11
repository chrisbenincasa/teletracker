CREATE INDEX "thing_name_full_text_simple_idx" ON "things" USING GIN (to_tsvector('simple', name));
CREATE INDEX "person_name_full_text_simple_idx" ON "people" USING GIN (to_tsvector('simple', name));