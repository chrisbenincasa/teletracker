export const USER_AGENT_STRING =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

const alternateAgents = [
  USER_AGENT_STRING,
  'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36',
  'Mozilla/5.0 (Windows NT 5.1; rv:7.0.1) Gecko/20100101 Firefox/7.0.1',
];

export function randomUserAgent() {
  return alternateAgents[_.random(0, alternateAgents.length, false)];
}

export const DATA_BUCKET =
  process.env.DATA_BUCKET || 'teletracker-data-us-west-2';
