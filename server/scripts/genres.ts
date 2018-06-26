import request = require('request-promise-native');
import { MovieDbClient } from 'themoviedb-client-typed';
import GlobalConfig from '../src/Config';
import { slugify } from '../src/util/Slug';

async function main() {
    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    // let justwatchGenres = await request('https://apis.justwatch.com/content/genres/locale/en_US', { json: true, gzip: true });
    let movieDbGenres = await movieDbClient.genres.getMovieGenres();

    movieDbGenres.forEach(genre => {
        console.log(slugify(genre.name));
    });
    process.exit(0);
}

main();