import { put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { TeletrackerResponse } from '../../utils/api-client';
import _ from 'lodash';
import {
  ItemType,
  SortOptions,
  NetworkType,
  OpenRange,
  Paging,
} from '../../types';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';

export const SEARCH_INITIATED = 'search/INITIATED';
export const SEARCH_PRELOAD_INITIATED = 'search/preload/INITIATED';
export const SEARCH_SUCCESSFUL = 'search/SUCCESSFUL';
export const SEARCH_FAILED = 'search/FAILED';

export interface SearchInitiatedPayload {
  query: string;
  bookmark?: string;
  limit?: number;
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  genres?: number[];
  releaseYearRange?: OpenRange;
  sort?: SortOptions;
}

export type SearchInitiatedAction = FSA<
  typeof SEARCH_INITIATED | typeof SEARCH_PRELOAD_INITIATED,
  SearchInitiatedPayload
>;
export interface SearchSuccessfulPayload {
  results: Item[];
  paging?: Paging;
  append: boolean;
}

export type SearchSuccessfulAction = FSA<
  typeof SEARCH_SUCCESSFUL,
  SearchSuccessfulPayload
>;

// TODO: Could fold this into a single action type "SearchCompleted"
export type SearchFailedAction = FSA<typeof SEARCH_FAILED, Error>;

export const SearchInitiated = createAction<SearchInitiatedAction>(
  SEARCH_INITIATED,
);
export const PreloadSearchInitiated = createAction<SearchInitiatedAction>(
  SEARCH_PRELOAD_INITIATED,
);
export const SearchSuccess = createAction<SearchSuccessfulAction>(
  SEARCH_SUCCESSFUL,
);
export const SearchFailed = createAction<SearchFailedAction>(SEARCH_FAILED);

export type SearchActionTypes =
  | SearchFailedAction
  | SearchInitiatedAction
  | SearchSuccessfulAction;

export const searchSaga = function*() {
  yield takeLatest(SEARCH_INITIATED, function*({
    payload,
  }: SearchInitiatedAction) {
    if (payload) {
      try {
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.searchV2,
          payload.query,
          payload.bookmark,
          payload.limit,
          payload.itemTypes,
          payload.networks,
          payload.genres,
          payload.releaseYearRange,
          payload.sort,
        );

        if (response.ok) {
          let successPayload = {
            results: response.data!.data.map(ItemFactory.create),
            paging: response.data!.paging,
            append: !_.isUndefined(payload!.bookmark),
          };

          yield put(SearchSuccess(successPayload));
        } else {
          yield put(SearchFailed(new Error(response.problem)));
        }
      } catch (e) {
        yield put(SearchFailed(e));
      }
    }
  });
};

export const search = (payload: SearchInitiatedPayload) => {
  return SearchInitiated(payload);
};
