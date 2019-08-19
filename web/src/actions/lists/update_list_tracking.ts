import { put, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { FSA } from 'flux-standard-action';
import { retrieveAllLists } from './retrieve_all_lists';

export const LIST_UPDATE_INITIATED = 'lists/update/INITIATED';
export const LIST_UPDATE_SUCCESS = 'lists/update/SUCCESS';
export const LIST_UPDATE_FAILED = 'lists/update/FAILED';

export interface ListUpdatedInitiatedPayload {
  thingId: string;
  addToLists: string[];
  removeFromLists: string[];
}

export type ListUpdateInitiatedAction = FSA<
  typeof LIST_UPDATE_INITIATED,
  ListUpdatedInitiatedPayload
>;

export const ListUpdate = createAction<ListUpdateInitiatedAction>(
  LIST_UPDATE_INITIATED,
);

export const updateListSaga = function*() {
  yield takeLatest(LIST_UPDATE_INITIATED, function*({
    payload,
  }: ListUpdateInitiatedAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateListTracking,
        payload.thingId,
        payload.addToLists,
        payload.removeFromLists,
      );

      if (response.ok) {
        yield put(retrieveAllLists({}));
      }
    } else {
    }
  });
};
