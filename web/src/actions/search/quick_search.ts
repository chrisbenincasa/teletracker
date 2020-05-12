import { put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { TeletrackerResponse } from '../../utils/api-client';
import _ from 'lodash';
import { Paging } from '../../types';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';
import { FilterParams } from '../../utils/searchFilters';

export const QUICK_SEARCH_INITIATED = 'search/quick/INITIATED';
export const QUICK_SEARCH_SUCCESSFUL = 'search/quick/SUCCESSFUL';
export const QUICK_SEARCH_FAILED = 'search/quick/FAILED';

export interface QuickSearchInitiatedPayload {
  query: string;
  bookmark?: string;
  limit?: number;
  filters?: FilterParams;
}

export type QuickSearchInitiatedAction = FSA<
  typeof QUICK_SEARCH_INITIATED,
  QuickSearchInitiatedPayload
>;

export interface QuickSearchSuccessfulPayload {
  results: Item[];
  paging?: Paging;
  append: boolean;
}

export type QuickSearchSuccessfulAction = FSA<
  typeof QUICK_SEARCH_SUCCESSFUL,
  QuickSearchSuccessfulPayload
>;

// TODO: Could fold this into a single action type "SearchCompleted"
export type QuickSearchFailedAction = FSA<typeof QUICK_SEARCH_FAILED, Error>;

const QuickSearchInitiated = createAction<QuickSearchInitiatedAction>(
  QUICK_SEARCH_INITIATED,
);
const QuickSearchSuccess = createAction<QuickSearchSuccessfulAction>(
  QUICK_SEARCH_SUCCESSFUL,
);
const QuickSearchFailed = createAction<QuickSearchFailedAction>(
  QUICK_SEARCH_FAILED,
);

export type QuickSearchActionTypes =
  | QuickSearchFailedAction
  | QuickSearchInitiatedAction
  | QuickSearchSuccessfulAction;

export const quickSearchSaga = function*() {
  yield takeLatest(QUICK_SEARCH_INITIATED, function*({
    payload,
  }: QuickSearchInitiatedAction) {
    if (payload) {
      try {
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.search,
          {
            searchText: payload.query,
            itemTypes: payload.filters?.itemTypes,
            networks: payload.filters?.networks,
            bookmark: payload.bookmark,
            sort: payload.filters?.sortOrder,
            limit: payload.limit,
            genres: payload.filters?.genresFilter,
            releaseYearRange: payload.filters?.sliders?.releaseYear,
            castIncludes: payload.filters?.people,
            imdbRating: payload.filters?.sliders?.imdbRating,
          },
        );

        if (response.ok) {
          let successPayload = {
            results: response.data!.data.map(ItemFactory.create),
            paging: response.data!.paging,
            append: !_.isUndefined(payload!.bookmark),
          };

          yield put(QuickSearchSuccess(successPayload));
        } else {
          yield put(QuickSearchFailed(new Error(response.problem)));
        }
      } catch (e) {
        yield put(QuickSearchFailed(e));
      }
    }
  });
};

export const quickSearch = (payload: QuickSearchInitiatedPayload) => {
  return QuickSearchInitiated(payload);
};
