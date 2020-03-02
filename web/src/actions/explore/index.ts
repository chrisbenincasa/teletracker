import {
  ItemType,
  SortOptions,
  NetworkType,
  OpenRange,
  Paging,
} from '../../types';
import { ErrorFSA, FSA } from 'flux-standard-action';
import { Item, ItemFactory } from '../../types/v2/Item';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { ApiItem } from '../../types/v2';
import _ from 'lodash';

export const EXPLORE_INITIATED = 'explore/INITIATED';
export const EXPLORE_SUCCESSFUL = 'explore/SUCCESSFUL';
export const EXPLORE_FAILED = 'explore/FAILED';

export interface ExploreInitiatedActionPayload {
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  bookmark?: string;
  sort?: SortOptions;
  limit?: number;
  genres?: number[];
  releaseYearRange?: OpenRange;
  cast?: string[];
}

export type ExploreInitiatedAction = FSA<
  typeof EXPLORE_INITIATED,
  ExploreInitiatedActionPayload
>;

export interface ExploreSuccessfulPayload {
  items: Item[];
  paging?: Paging;
  append: boolean;
}

export type ExploreSuccessfulAction = FSA<
  typeof EXPLORE_SUCCESSFUL,
  ExploreSuccessfulPayload
>;

export type ExploreFailedAction = ErrorFSA<
  Error,
  undefined,
  typeof EXPLORE_FAILED
>;

export const retrieveExplore = createAction<ExploreInitiatedAction>(
  EXPLORE_INITIATED,
);

export const exploreSuccess = createAction<ExploreSuccessfulAction>(
  EXPLORE_SUCCESSFUL,
);

export const exploreFailed = createAction<ExploreFailedAction>(EXPLORE_FAILED);

export const exploreSaga = function*() {
  yield takeEvery(EXPLORE_INITIATED, function*({
    payload,
  }: ExploreInitiatedAction) {
    if (payload) {
      try {
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.getItems,
          payload.itemTypes,
          payload.networks,
          payload.bookmark,
          payload.sort,
          payload.limit,
          payload.genres,
          payload.releaseYearRange,
          payload.cast,
        );

        if (response.ok) {
          yield put(
            exploreSuccess({
              items: response.data!.data.map(ItemFactory.create),
              paging: response.data!.paging,
              append: !_.isUndefined(payload.bookmark),
            }),
          );
        }
      } catch (e) {
        console.error(e);
        yield put(exploreFailed(e));
      }
    }
  });
};
