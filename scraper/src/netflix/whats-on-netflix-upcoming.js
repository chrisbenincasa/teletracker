import request from 'request-promise';
import cheerio from 'cheerio';
import { USER_AGENT_STRING } from '../common/constants';
import moment from 'moment';

const TITLE_REGEX = /What's Coming to Netflix on ([A-z]+ [0-9a-z]+).*/i;
const nameAndYearRegex = /^(.*)\s\((\d+)\)(\s?N)$/i;

export const scrape = async event => {
  try {
    const url = event.url;
    const year = event.year || moment().year();

    if (!url) {
      throw new Error('Need url');
    }

    const html = await request.get(url, {
      headers: {
        'User-Agent': USER_AGENT_STRING,
      },
    });

    const $ = cheerio.load(html);

    const headers = $('h4')
      .filter((_, el) =>
        $(el)
          .text()
          .toLowerCase()
          .includes('netflix on'),
      )
      .get();

    headers.forEach(header => {
      const $el = $(header);
      const text = $el
        .text()
        .replace(/[\u2018\u2019]/g, "'")
        .replace(/[\u201C\u201D]/g, '"');

      const matches = TITLE_REGEX.exec(text);
      if (matches && matches.length > 1) {
        const date = moment(matches[1], 'MMMM Do');
        console.log(date.format());
      }

      const list = $el.nextAll('ul').first();
      if (list) {
        const listItems = list
          .find('li')
          .map((_, li) =>
            $(li)
              .children('strong')
              .text(),
          )
          .get()
          .filter(x => x.trim().length > 0);
        console.log(listItems);
      }
    });
  } catch (e) {
    console.error(e);
  }
};
