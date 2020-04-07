import request from 'request-promise';
import ip from 'public-ip';
import { resolveSecret } from './common/aws_util';

const BASE_URL = 'https://api.themoviedb.org/3';

export default async function scrape(event: any) {
  console.log(await ip.v4());

  console.log(event);

  const TMDB_API_KEY =
    process.env.TMDB_API_KEY || (await resolveSecret('tmdb-api-key-qa'));
  const result = await request.get(
    `${BASE_URL}/tv/${event.id}/season/${event.seasonId}`,
    {
      qs: {
        api_key: TMDB_API_KEY,
      },
      json: true,
    },
  );

  console.log(result);
}
