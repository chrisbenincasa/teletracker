import { SearchResultTuple, UnogsApiClient } from './util/UnogsApiClient';

// http://unogs.com/nf.cgi?u=4unogs&q=Breaking%20Bad&t=ns&cl=&st=bs&ob=&p=1&l=100&inc=&ao=and


async function main() {
    const client = new UnogsApiClient();

    const re = await client.searchQuery('Breaking Bad');
    // const re: any = await getTitle('80021955');

    // console.log(R.find<string>(x => x.startsWith('tt'))(re.RESULT.imdbinfo));
    console.log(re);
}

main();