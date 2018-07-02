import request = require('request-promise-native');
import * as striptags from 'striptags';
const Entities = require('html-entities').AllHtmlEntities;

type NetflixId = string
type Title = string
type PosterUrl = string
type UnsanitizedDescription = string
type PermiereYear = string

type CountryAvailability = object;
type IsAvailableForDownload = string;
type IMDBTop250Rating = string | '';

export type SearchResultTuple = [
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
    netflixId?: string // 0
    title?: string, // 1
    posterUrl?: string, // 2
    description?: string, // 3
    // dupe 4
    // unknown 5
    // unknown 6 
    premiereYear?: string, // 7
    // unknown 8
    // unknown 9
    countryAvailability: object,
    isAvailableForDownload?: boolean,
    imdbTop250Rating?: string
}

type ImdbRating = string // number
type ImdbVoteCount = string // number

type ImdbInfoTuple = [
    ImdbRating,
    ImdbVoteCount,
    string, // metascore
    string, // genre
    string, // nomninations
    string, // runtime
    string, // description
    string, // origin country
    string, // languages
    string, // imdb id
    string, // some kinda date
    string // top 250 rank
]

export type Availability = {
    country?: string,
    countryCode?: string,
    numSeasons?: number
    perSeasonAvailability: Map<number, number>
}

type CountryAvailabilityTuple = [
    string, // country
    string, // two-letter country code
    string, // season availability
    string, // first available on?
    string, // ???
    string[], // per season breakdown
    string[], // Audio languages
    string[], // subtitle languages
    string, // ???
    string, // ???
    string, // ???
    string // ???
]

type ImdbInfo = {
    rating?: number,
    voteCount?: number,
    imdbId?: string
}

export type DetailResult = {
    imdbInfo: ImdbInfo,
    country: Availability[]
}

export type RawDetailResult = {
    RESULT: {
        nfInfo: string[],
        imdbinfo: ImdbInfoTuple,
        country: CountryAvailabilityTuple[]
    }
}

export type RawSearchResult = {
    ITEMS: SearchResultTuple[]
}

export enum UnogsSearchSort {
    "Relevance", 
    "Date", 
    "Rating", 
    "IMDBRating", 
    "TomatoRating", 
    "Title", 
    "VideoType", 
    "FilmYear", 
    "Runtime"
}

export class UnogsApiClient { 
    private entities = new Entities();

    private async makeRequest(qs: any) {
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

    private processCountryTuples(countryTuples: CountryAvailabilityTuple[]): Availability[] {
        return countryTuples.map(tuple => {
            let seasonAvail = new Map<number, number>();

            tuple[5].forEach(season => {
                let sanitized = this.entities.decode(striptags(season));
                let result = /(\d+)\((\d+)\)/.exec(sanitized);
                if (result) {
                    let [_, seasonNum, numEpisodesAvail, ...rest] = result;
                    seasonAvail.set(parseInt(seasonNum), parseInt(numEpisodesAvail));
                }
            })

            return {
                country: tuple[0].trim(),
                countryCode: tuple[1].trim(),
                numSeasons: parseInt(striptags(tuple[2])),
                perSeasonAvailability: seasonAvail
            } as Availability;
        });
    }

    private processImdbTuple(imdbTuple: ImdbInfoTuple): ImdbInfo {
        return {
            rating: parseFloat(imdbTuple[0]),
            voteCount: parseFloat(imdbTuple[1]),
            imdbId: imdbTuple[9]
        } as ImdbInfo;
    }

    private processSearchTuple(searchResultTuple: SearchResultTuple): SearchResult {
        return {
            netflixId: searchResultTuple[0],
            title: searchResultTuple[1],
            posterUrl: searchResultTuple[2],
            description: searchResultTuple[4],
            premiereYear: searchResultTuple[7],
            countryAvailability: searchResultTuple[10],
            isAvailableForDownload: searchResultTuple[11] == '1',
            imdbTop250Rating: searchResultTuple[12]
        } as SearchResult;
    }

    async searchQuery(query: string, sort?: UnogsSearchSort): Promise<SearchResult[]> {
        const q = {
            u: '4unogs',
            q: query,
            t: 'ns',
            cl: '',
            st: 'bs',
            ob: sort || 'Relevance',
            l: 100,
            inc: '',
            ao: 'and'
        }

        return this.makeRequest(q).then((x: RawSearchResult) => x.ITEMS.map(this.processSearchTuple));
    }

    async getTitleQuery(query: string): Promise<DetailResult> {
        const q = {
            u: '4unogs',
            t: 'loadvideo',
            q: query,
            cl: ''
        };

        return this.makeRequest(q).then((x: RawDetailResult) => {
            return {
                imdbInfo: this.processImdbTuple(x.RESULT.imdbinfo),
                country: this.processCountryTuples(x.RESULT.country)
            } as DetailResult;
        });
    }
}