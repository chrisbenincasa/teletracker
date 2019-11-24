import request from 'request-promise';
import cheerio from 'cheerio';
import { USER_AGENT_STRING } from '../common/constants';

export const scrape = async event => {
  try {
    const url = event.url;

    if (!url) {
      throw new Error('Need url');
    }

    const html = await request.get(url, {
      headers: {
        'User-Agent': USER_AGENT_STRING,
      },
    });

    const $ = cheerio.load(html);

    const listItems = $('ul')
      .filter((_, el) => !$(el).classList)
      .map((_, el) =>
        $(el)
          .find('li')
          .map((_, li) =>
            $(li)
              .children('strong')
              .text(),
          )
          .get()
          .filter(x => x.trim().length > 0),
      )
      .get();

    console.log(listItems);
  } catch (e) {
    console.error(e);
  }
};
