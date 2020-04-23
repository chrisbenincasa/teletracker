import { put, takeEvery } from '@redux-saga/core/effects';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { ApiItem } from '../../types/v2';
import { Item } from '../../types/v2/Item';

export const ITEM_FETCH_INITIATED = 'item-detail/INITIATED';
export const ITEM_FETCH_SUCCESSFUL = 'item-detail/SUCCESSFUL';
export const ITEM_PREFETCH_SUCCESSFUL = 'item-detail/PREFETCH_SUCCESSFUL';
export const ITEM_FETCH_FAILED = 'item-detail/FAILED';

export interface ItemFetchInitiatedPayload {
  id: string | number;
  type: string;
}

export type ItemFetchInitiatedAction = FSA<
  typeof ITEM_FETCH_INITIATED,
  ItemFetchInitiatedPayload
>;

export interface ItemFetchSuccessfulPayload {
  item: ApiItem;
  includedRecommendations: boolean;
}

export type ItemFetchSuccessfulAction = FSA<
  typeof ITEM_FETCH_SUCCESSFUL,
  ItemFetchSuccessfulPayload
>;

export type ItemPrefetchSuccessfulAction = FSA<
  typeof ITEM_PREFETCH_SUCCESSFUL,
  Item
>;

export type ItemFetchFailedAction = ErrorFluxStandardAction<
  typeof ITEM_FETCH_FAILED,
  Error
>;

export const itemFetchInitiated = createAction<ItemFetchInitiatedAction>(
  ITEM_FETCH_INITIATED,
);

export const itemFetchSuccess = createAction<ItemFetchSuccessfulAction>(
  ITEM_FETCH_SUCCESSFUL,
);

export const itemPrefetchSuccess = createAction<ItemPrefetchSuccessfulAction>(
  ITEM_PREFETCH_SUCCESSFUL,
);

const itemFetchFailed = createAction<ItemFetchFailedAction>(ITEM_FETCH_FAILED);

export const fetchItemDetailsSaga = function*() {
  yield takeEvery(ITEM_FETCH_INITIATED, function*({
    payload,
  }: ItemFetchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(
        client => client.getItem,
        payload.id,
        payload.type,
        true,
      );

      if (response.ok) {
        yield put(
          itemFetchSuccess({
            item: response.data.data,
            includedRecommendations: true,
          }),
        );
      } else {
        yield put(itemFetchFailed(new Error()));
      }
    }
  });
};
