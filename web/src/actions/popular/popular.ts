import { call, put, takeEvery } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import _ from 'lodash';
import { Paging } from '../../types';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FilterParams } from '../../utils/searchFilters';
import { logException } from '../../utils/analytics';

export const POPULAR_INITIATED = 'popular/INITIATED';
export const POPULAR_SUCCESSFUL = 'popular/SUCCESSFUL';
export const POPULAR_FAILED = 'popular/FAILED';
export const POPULAR_CLEAR = 'popular/CLEAR';

export interface PopularInitiatedActionPayload {
  filters?: FilterParams;
  bookmark?: string;
  limit?: number;
}

export type PopularInitiatedAction = FSA<
  typeof POPULAR_INITIATED,
  PopularInitiatedActionPayload
>;

export interface PopularSuccessfulPayload {
  popular: Item[];
  paging?: Paging;
  append: boolean;
  forFilters?: FilterParams;
}

export type PopularSuccessfulAction = FSA<
  typeof POPULAR_SUCCESSFUL,
  PopularSuccessfulPayload
>;

export type PopularFailedAction = FSA<typeof POPULAR_FAILED, Error>;

export type PopularClearAction = FSA<typeof POPULAR_CLEAR>;

export const retrievePopular = createAction<PopularInitiatedAction>(
  POPULAR_INITIATED,
);

export const popularSuccess = createAction<PopularSuccessfulAction>(
  POPULAR_SUCCESSFUL,
);

export const popularFailed = createAction<PopularFailedAction>(POPULAR_FAILED);

export const clearPopular = createBasicAction<PopularClearAction>(
  POPULAR_CLEAR,
);

export const popularSaga = function*() {
  yield takeEvery(POPULAR_INITIATED, function*({
    payload,
  }: PopularInitiatedAction) {
    if (payload) {
      try {
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.getItems,
          {
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
          yield put(
            popularSuccess({
              popular: response.data!.data.map(ItemFactory.create),
              paging: response.data!.paging,
              append: !_.isUndefined(payload.bookmark),
              forFilters: payload.filters,
            }),
          );
        }
      } catch (e) {
        console.error(e);
        call(logException, `${e}`, false);
        yield put(popularFailed(e));
      }
    }
  });
};
