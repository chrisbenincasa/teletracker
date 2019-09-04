CREATE INDEX "person_by_slug" ON people (normalized_name);
CREATE INDEX "person_by_tmdb_id" ON people (tmdb_id);