import { SEARCH_INITIATED, SEARCH_SUCCESSFUL } from '../constants/search';
import { Dispatch } from 'redux';
import { TeletrackerApi } from '../utils/api-client';

interface SearchInitiatedAction {
  type: typeof SEARCH_INITIATED;
  text: string;
}

interface SearchSuccessfulAction {
  type: typeof SEARCH_SUCCESSFUL;
  results: any;
}

export const searchInitiated: (
  text: string,
) => SearchInitiatedAction = text => ({
  type: SEARCH_INITIATED,
  text,
});

export const searchSuccess: (
  results: any,
) => SearchSuccessfulAction = results => ({
  type: SEARCH_SUCCESSFUL,
  results,
});

export type SearchActionTypes = SearchInitiatedAction | SearchSuccessfulAction;

const client = TeletrackerApi.instance;

export const search = (text: string) => {
  return async (dispatch: Dispatch) => {
    dispatch(searchInitiated(text));

    return client.search(text).then(response => {
      if (response.ok) {
        console.log(response.data);
        dispatch(searchSuccess(response.data));
      }
    });
  };
};
