CREATE TABLE popular_items(
    id SERIAL NOT NULL PRIMARY KEY,
    thing_id UUID NOT NULL,
    ordering INTEGER NOT NULL,
    type VARCHAR NOT NULL,
    popular_as_of timestamp WITH time zone NOT NULL
);

CREATE INDEX "popular_items_thing_idx" ON popular_items (thing_id, popular_as_of, type, ordering DESC);