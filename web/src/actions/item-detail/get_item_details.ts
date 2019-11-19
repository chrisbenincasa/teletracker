import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction } from '../utils';
import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { ApiItem } from '../../types/v2';

export const ITEM_FETCH_INITIATED = 'item-detail/INITIATED';
export const ITEM_FETCH_SUCCESSFUL = 'item-detail/SUCCESSFUL';
export const ITEM_FETCH_FAILED = 'item-detail/FAILED';

export interface ItemFetchInitiatedPayload {
  id: string | number;
  type: string;
}

export type ItemFetchInitiatedAction = FSA<
  typeof ITEM_FETCH_INITIATED,
  ItemFetchInitiatedPayload
>;

export type ItemFetchSuccessfulAction = FSA<
  typeof ITEM_FETCH_SUCCESSFUL,
  ApiItem
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
      );

      if (response.ok) {
        yield put(itemFetchSuccess(response.data.data));
      } else {
        yield put(itemFetchFailed(new Error()));
      }
    }
  });
};
