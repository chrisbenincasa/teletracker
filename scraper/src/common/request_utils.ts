import request from 'request-promise';
import { USER_AGENT_STRING } from './constants';

export const httpGet = async <T = any>(url: string) => {
  return request.get({
    uri: url,
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });
};
