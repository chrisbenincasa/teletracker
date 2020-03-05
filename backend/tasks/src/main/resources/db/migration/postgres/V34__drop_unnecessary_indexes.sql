DROP INDEX "thing_name_full_text_idx"; -- The "simple" configuration is better for our needs.

DROP INDEX "things_popularity_idx"; -- Had to specify DESC NULLS LAST in index.