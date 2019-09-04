import { put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { clientEffect, createAction } from '../utils';
import Thing, { ApiThing, ThingFactory } from '../../types/Thing';

export const SEARCH_INITIATED = 'search/INITIATED';
export const SEARCH_SUCCESSFUL = 'search/SUCCESSFUL';
export const SEARCH_FAILED = 'search/FAILED';
export type SearchInitiatedAction = FSA<typeof SEARCH_INITIATED, string>;
export interface SearchSuccessfulPayload {
  results: Thing[];
}

export type SearchSuccessfulAction = FSA<
  typeof SEARCH_SUCCESSFUL,
  SearchSuccessfulPayload
>;

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
        let payload = {
          results: (response.data.data as ApiThing[]).map(thing =>
            ThingFactory.create(thing),
          ),
        };

        yield put(SearchSuccess(payload));
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
