import request from 'request-promise-native';
import * as querystring from 'querystring';

const base = 'https://apis.justwatch.com';

async function getMovie(id: string) {
    return request(`${base}/content/titles/movie/${id}/locale/en_US`, {json: true});
}

async function getTvShow(id: string) {
    return request(`${base}/content/titles/show/${id}/locale/en_US`, {json: true});
}

async function getAllProviders() {
    return request(`${base}/content/provides/locale/en_US`, {json: true});
}

enum Provider {
    Netflix = "nfx"
}

// {"age_certifications":null,"content_types":null,"genres":null,"languages":null,"max_price":null,"min_price":null,"page":1,"page_size":30,"presentation_types":null,"providers":null,"query":"the matrix","release_year_from":null,"release_year_until":null,"scoring_filter_types":null,"timeline_type":null}
interface PopularSearch {
    age_certifications?: any,
    content_types?: any,
    genres?: any,
    languages?: any,
    max_price?: any,
    min_price?: any,
    page: number,
    page_size: number,
    presentation_types?: any,
    providers?: Provider[],
    query?: any,
    release_year_from?: any,
    release_year_until?: any,
    scoring_filter_types?: any
    timeline_type?: any
}

async function popular(search: PopularSearch) {
    return request(`${base}/content/titles/en_US/popular`, { qs: { body: JSON.stringify(search) }, useQuerystring: true, json: true });
}

async function main() {
    let res = await popular({ page: 1, page_size: 10, query: 'the matrix', providers: [Provider.Netflix] });
    console.log(res.items[0]);
}

main();