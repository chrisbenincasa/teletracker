CREATE INDEX "thing_name_full_text_idx" ON "things" USING GIN (to_tsvector('english', name));