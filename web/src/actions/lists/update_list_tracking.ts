import { put, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { FSA } from 'flux-standard-action';
import { retrieveAllLists } from './retrieve_all_lists';
import ReactGA from 'react-ga';

export const LIST_UPDATE_TRACKING_INITIATED = 'lists/update_tracking/INITIATED';
export const LIST_UPDATE_TRACKING_SUCCESS = 'lists/update_tracking/SUCCESS';
export const LIST_UPDATE_TRACKING_FAILED = 'lists/update_tracking/FAILED';

export interface ListTrackingUpdatedInitiatedPayload {
  thingId: string;
  addToLists: string[];
  removeFromLists: string[];
}

export type ListTrackingUpdateInitiatedAction = FSA<
  typeof LIST_UPDATE_TRACKING_INITIATED,
  ListTrackingUpdatedInitiatedPayload
>;

export const updateListTracking = createAction<
  ListTrackingUpdateInitiatedAction
>(LIST_UPDATE_TRACKING_INITIATED);

export const updateListTrackingSaga = function*() {
  yield takeLatest(LIST_UPDATE_TRACKING_INITIATED, function*({
    payload,
  }: ListTrackingUpdateInitiatedAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateListTracking,
        payload.thingId,
        payload.addToLists,
        payload.removeFromLists,
      );

      if (response.ok) {
        yield put(retrieveAllLists({}));

        ReactGA.event({
          category: 'User',
          action: 'Update list',
        });
      }
    } else {
    }
  });
};
