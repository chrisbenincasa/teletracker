ALTER TABLE "person_things" DROP CONSTRAINT "person_thing_prim_key";
ALTER TABLE "person_things" ADD PRIMARY KEY ("person_id", "thing_id", "relation_type");