import { put, takeEvery } from '@redux-saga/core/effects';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { ApiItem, Id } from '../../types/v2';
import { Item } from '../../types/v2/Item';

export const ITEM_RECOMMENDATIONS_FETCH_INITIATED =
  'item-detail/recommendations/INITIATED';
export const ITEM_RECOMMENDATIONS_FETCH_SUCCESSFUL =
  'item-detail/recommendations/SUCCESSFUL';
export const ITEM_PREFETCH_SUCCESSFUL =
  'item-detail/recommendations/PREFETCH_SUCCESSFUL';
export const ITEM_RECOMMENDATIONS_FETCH_FAILED =
  'item-detail/recommendations/FAILED';

export interface ItemRecsFetchInitiatedPayload {
  id: Id;
  type: string;
}

export type ItemRecsFetchInitiatedAction = FSA<
  typeof ITEM_RECOMMENDATIONS_FETCH_INITIATED,
  ItemRecsFetchInitiatedPayload
>;

export interface ItemRecsFetchSuccessfulPayload {
  forItem: Id;
  items: ApiItem[];
}

export type ItemRecsFetchSuccessfulAction = FSA<
  typeof ITEM_RECOMMENDATIONS_FETCH_SUCCESSFUL,
  ItemRecsFetchSuccessfulPayload
>;

export type ItemPrefetchSuccessfulAction = FSA<
  typeof ITEM_PREFETCH_SUCCESSFUL,
  Item
>;

export type ItemRecsFetchFailedAction = ErrorFluxStandardAction<
  typeof ITEM_RECOMMENDATIONS_FETCH_FAILED,
  Error
>;

export const itemRecommendationsFetchInitiated = createAction<
  ItemRecsFetchInitiatedAction
>(ITEM_RECOMMENDATIONS_FETCH_INITIATED);

export const itemRecommendationsFetchSuccess = createAction<
  ItemRecsFetchSuccessfulAction
>(ITEM_RECOMMENDATIONS_FETCH_SUCCESSFUL);

export const itemPrefetchSuccess = createAction<ItemPrefetchSuccessfulAction>(
  ITEM_PREFETCH_SUCCESSFUL,
);

const itemRecommendationsFetchFailed = createAction<ItemRecsFetchFailedAction>(
  ITEM_RECOMMENDATIONS_FETCH_FAILED,
);

export const fetchItemRecsSaga = function*() {
  yield takeEvery(ITEM_RECOMMENDATIONS_FETCH_INITIATED, function*({
    payload,
  }: ItemRecsFetchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(
        client => client.getItemRecommendations,
        payload.id,
        payload.type,
      );

      if (response.ok) {
        yield put(
          itemRecommendationsFetchSuccess({
            forItem: payload.id,
            items: response.data!.data,
          }),
        );
      } else {
        yield put(itemRecommendationsFetchFailed(new Error()));
      }
    }
  });
};
