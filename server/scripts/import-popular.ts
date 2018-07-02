import { Movie, MovieDbClient, PagedResult } from 'themoviedb-client-typed';
import { createConnection } from 'typeorm';

import GlobalConfig from '../src/Config';
import { MovieImporter } from '../src/util/MovieImporter';

async function main(args: string[]) {
    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    let provider: (page: number) => Promise<PagedResult<Partial<Movie>>>;
    let pages = args.length > 1 ? args[1] : 5;

    if (args[0] === 'popular') {
        provider = (page: number) => movieDbClient.movies.getPopular(null, page);
    } else if (args[0] === 'now_playing') {
        provider = (page: number) => movieDbClient.movies.getNowPlaying(null, page);
    } else if (args[0] === 'top_rated') {
        provider = (page: number) => movieDbClient.movies.getTopRated(null, page);
    } else if (args[0] === 'upcoming') {
        provider = (page: number) => movieDbClient.movies.getUpcoming(null, page);
    } else {
        console.log('Movie type ' + args[0] + ' not supported');
        return;
    }

    let connection = await createConnection(GlobalConfig.db);
    let importer = new MovieImporter(connection);

    for (let i = 1; i <= pages; i++) {
        console.log(`Pulling page ${i} of TMDb's ${args[0]} movies`)
        let popular = await provider(i);
        
        console.log(`Got ${popular.results.length} ${args[0]} from TMDb`);

        await importer.handleMovies(popular.results.entries());
    }

    process.exit(0);
}

const args = process.argv.slice(2);

try {
    main(args);
} catch (e) {
    console.error(e);
}