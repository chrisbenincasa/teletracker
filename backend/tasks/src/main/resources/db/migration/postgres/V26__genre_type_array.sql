ALTER TABLE genres DROP COLUMN type;
ALTER TABLE genres ADD COLUMN type varchar(15)[];