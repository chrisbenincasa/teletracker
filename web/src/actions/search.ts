import { put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import {
  SEARCH_FAILED,
  SEARCH_INITIATED,
  SEARCH_SUCCESSFUL,
} from '../constants/search';
import { clientEffect, createAction } from './utils';

export type SearchInitiatedAction = FSA<typeof SEARCH_INITIATED, string>;
export type SearchSuccessfulAction = FSA<typeof SEARCH_SUCCESSFUL, any>;

// TODO: Could fold this into a single action type "SearchCompleted"
export type SearchFailedAction = FSA<typeof SEARCH_FAILED, Error>;

const SearchInitiated = createAction<SearchInitiatedAction>(SEARCH_INITIATED);
const SearchSuccess = createAction<SearchSuccessfulAction>(SEARCH_SUCCESSFUL);
const SearchFailed = createAction<SearchFailedAction>(SEARCH_FAILED);

export type SearchActionTypes =
  | SearchFailedAction
  | SearchInitiatedAction
  | SearchSuccessfulAction;

export const searchSaga = function*() {
  yield takeLatest(SEARCH_INITIATED, function*({
    payload,
  }: SearchInitiatedAction) {
    try {
      let response = yield clientEffect(client => client.search, payload!);

      if (response.ok) {
        yield put(SearchSuccess(response.data));
      } else {
        yield put(SearchFailed(response.problem));
      }
    } catch (e) {
      yield put(SearchFailed(e));
    }
  });
};

export const search = (text: string) => {
  return SearchInitiated(text);
};
