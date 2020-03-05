-- Must install this as superuser
-- CREATE EXTENSION intarray;

ALTER TABLE "things" ADD COLUMN "genres" int[];

CREATE INDEX "things_genres_with_intarray" ON things USING GIN(genres gin__int_ops);