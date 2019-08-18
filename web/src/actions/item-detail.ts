import {
  ITEM_BATCH_INITIATED,
  ITEM_FETCH_FAILED,
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
} from '../constants/item-detail';
import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { Thing } from '../types';
import { clientEffect, createAction } from './utils';
import { put, takeEvery } from '@redux-saga/core/effects';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';

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
  Thing
>;

export type ItemFetchFailedAction = ErrorFluxStandardAction<
  typeof ITEM_FETCH_FAILED,
  Error
>;

export interface ItemBatchInitiatedPayload {
  ids: number[];
  fields?: KeyMap<ObjectMetadata>;
}

export type ItemBatchInitiatedAction = FSA<
  typeof ITEM_BATCH_INITIATED,
  ItemBatchInitiatedPayload
>;

export const itemFetchInitiated = createAction<ItemFetchInitiatedAction>(
  ITEM_FETCH_INITIATED,
);

export const itemFetchSuccess = createAction<ItemFetchSuccessfulAction>(
  ITEM_FETCH_SUCCESSFUL,
);

export const retrieveItemBatch = createAction<ItemBatchInitiatedAction>(
  ITEM_BATCH_INITIATED,
);

const ItemFetchFailed = createAction<ItemFetchFailedAction>(ITEM_FETCH_FAILED);

export type ItemDetailActionTypes =
  | ItemFetchInitiatedAction
  | ItemFetchSuccessfulAction
  | ItemFetchFailedAction;

// export const fetchItemDetails = (id: string, type: string) => {
//   return async (dispatch: Dispatch) => {
//     dispatch(itemFetchInitiated(id));
//
//     // To do fix for shows and such
//     // just testing for now
//     return client
//       .getItem(id, type)
//       .then(response => {
//         if (response.ok) {
//           dispatch(itemFetchSuccess(response.data.data));
//         } else {
//           dispatch(ItemFetchFailed(new Error()));
//         }
//       })
//       .catch(e => {
//         console.error(e);
//
//         dispatch(ItemFetchFailed(e));
//       });
//   };
// };

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
        yield put(ItemFetchFailed(new Error()));
      }
    }
  });
};

export const fetchItemDetailsBatchSaga = function*() {
  yield takeEvery(ITEM_BATCH_INITIATED, function*({
    payload,
  }: ItemBatchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(
        client => client.getThingsBatch,
        payload.ids,
        payload.fields,
      );

      if (response.ok) {
        console.log(response.data);
      }
    }
  });
};
