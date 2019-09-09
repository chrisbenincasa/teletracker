insert into thing_genres (thing_id, genre_id)
select movie_genres.id, gr.genre_id as genre_id
from (select id, jsonb_array_elements(metadata -> 'themoviedb' -> 'movie' -> 'genres') ->> 'id' as tmdb_genre_id
      from things
      where type = 'movie'
        and id not in ('166669cf-aa93-4bf6-a36e-fd43b8060fdf', '856a4801-868a-410f-9c9f-30e41f218d11',
                       'eea7801c-0aa3-48db-91fe-34e66155ac2e', 'efe5cb65-2686-40c7-b1cb-e01cf165e185'))
         as movie_genres,
     genre_references gr
where gr.external_id = tmdb_genre_id
on conflict do nothing;

insert into thing_genres (thing_id, genre_id)
select show_genres.id, gr.genre_id as genre_id
from (select id, jsonb_array_elements(metadata -> 'themoviedb' -> 'show' -> 'genres') ->> 'id' as tmdb_genre_id
      from things
      where type = 'show')
         as show_genres,
     genre_references gr
where gr.external_id = tmdb_genre_id
on conflict do nothing;

-- update things set genres = subquery.genres from
--    (select thing_id as id, array_agg(genre_id) as genres from thing_genres group by thing_id) as subquery where things.id = subquery.id;