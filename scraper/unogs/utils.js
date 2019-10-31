import request from 'request-promise';

export const unogsHeaders = {
  Cookie:
    'cooksess=lv58tc0shun9jq3qgn1o0ft7u6; PHPSESSID=p8qbslkv9oma62ujrhrl684l45; sstring=get%3Aexp%3A78-\\u21and',
  'Accept-Encoding': 'gzip, deflate, br',
  'User-Agent':
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36',
  Accept: 'application/json, text/javascript, */*; q=0.0',
  Referer: 'https://unogs.com/countrydetail/',
  'X-Requested-With': 'XMLHttpRequest',
};

export default async function requestUnogs(url, query, headers = {}) {
  return request({
    uri: url,
    headers: {
      ...unogsHeaders,
      ...headers,
    },
    qs: query,
  });
}
