ALTER TABLE "user_thing_tags"
    ADD CONSTRAINT "user_thing_actions_by_user_thing_uniq"
        UNIQUE USING INDEX "user_thing_actions_by_user_thing_uniq";