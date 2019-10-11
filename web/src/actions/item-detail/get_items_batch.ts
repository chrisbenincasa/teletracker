import { takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction } from '../utils';
import {
  ItemBatchInitiatedAction,
  ItemBatchInitiatedPayload,
} from '../item-detail';
import { FSA } from 'flux-standard-action';
import { KeyMap, ObjectMetadata } from '../../types/external/themoviedb/Movie';

export const ITEM_BATCH_INITIATED = 'item-detail/batch/INITIATED';
export const ITEM_BATCH_SUCCESSFUL = 'item-defail/batch/SUCCESSFUL';
export const ITEM_BATCH_FAILED = 'item-defail/batch/FAILED';

export interface ItemBatchInitiatedPayload {
  ids: number[];
  fields?: KeyMap<ObjectMetadata>;
}

export type ItemBatchInitiatedAction = FSA<
  typeof ITEM_BATCH_INITIATED,
  ItemBatchInitiatedPayload
>;

export const retrieveItemBatch = createAction<ItemBatchInitiatedAction>(
  ITEM_BATCH_INITIATED,
);

export const fetchItemDetailsBatchSaga = function*() {
  yield takeEvery(ITEM_BATCH_INITIATED, function*({
    payload,
  }: ItemBatchInitiatedAction) {
    if (payload) {
      let response: any = yield clientEffect(
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
