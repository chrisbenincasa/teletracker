import * as R from 'ramda';
import * as striptags from 'striptags';
import request = require('request-promise-native');

// http://unogs.com/nf.cgi?u=4unogs&q=Breaking%20Bad&t=ns&cl=&st=bs&ob=&p=1&l=100&inc=&ao=and

type NetflixId = string
type Title = string
type PosterUrl = string
type UnsanitizedDescription = string
type PermiereYear = string

type CountryAvailability = object;
type IsAvailableForDownload = string;
type IMDBTop250Rating = string | '';

type SearchResultTuple = [
    NetflixId, 
    Title,
    PosterUrl,
    UnsanitizedDescription,
    NetflixId,
    string,
    string,
    PermiereYear,
    string,
    string,
    CountryAvailability,
    IsAvailableForDownload,
    IMDBTop250Rating
];

type SearchResult = {
    ITEMS: SearchResultTuple[]
}

async function main() {
    // Get single title
    const getTitleQuery = (query: string) => {
        return {
            u: '4unogs',
            t: 'loadvideo',
            q: query,
            cl: ''
        };
    }

    // Search
    const searchQuery = (query: string) => {
        return {
            u: '4unogs',
            q: query,
            t: 'ns',
            cl: '',
            st: 'bs',
            ob: 'IMDBRating', // sort? ["Relevance", "Date", "Rating", "IMDBRating", "TomatoRating", "Title", "VideoType", "FilmYear", "Runtime"]
            l: 100,
            inc: '',
            ao: 'and'
        }
    }

    // http://unogs.com/nf.cgi?u=4unogs&q=Breaking%20Bad&t=ns&cl=&st=bs&ob=&p=1&l=100&inc=&ao=and

    const makeRequest = (qs: any) => {
        return request('http://unogs.com/nf.cgi', {
            qs,
            headers: {
                'Referer': 'http://unogs.com',
                'User-Agent': 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.87 Safari/537.36'
            },
            gzip: true,
            json: true
        });
    }

    const getTitle = (t: string) => makeRequest(getTitleQuery(t));

    const search = (query: string) => makeRequest(searchQuery(query));
    
    // const re: SearchResult = await search('Breaking Bad');
    const re: any = await getTitle('80021955');

    // console.log(R.find<string>(x => x.startsWith('tt'))(re.RESULT.imdbinfo));
    console.log(re.RESULT);
}

main();