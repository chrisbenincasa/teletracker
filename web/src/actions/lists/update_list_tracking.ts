import { all, put, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FSA } from 'flux-standard-action';
import { retrieveAllLists } from './retrieve_all_lists';
import ReactGA from 'react-ga';
import { updateUserItemTagsSuccess } from '../user/update_user_tags';
import { removeUserItemTagsSuccess } from '../user/remove_user_tag';

import { ActionType } from '../../types';

export const LIST_UPDATE_TRACKING_INITIATED = 'lists/update_tracking/INITIATED';
export const LIST_UPDATE_TRACKING_SUCCESS = 'lists/update_tracking/SUCCESS';
export const LIST_UPDATE_TRACKING_FAILED = 'lists/update_tracking/FAILED';

export interface ListTrackingUpdatedInitiatedPayload {
  itemId: string;
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
        payload.itemId,
        payload.addToLists,
        payload.removeFromLists,
      );

      if (response.ok) {
        yield put(retrieveAllLists({}));

        yield all(
          payload.addToLists.map(listId => {
            return put(
              updateUserItemTagsSuccess({
                itemId: payload.itemId,
                action: ActionType.TrackedInList,
                string_value: listId,
                unique: true,
              }),
            );
          }),
        );

        yield all(
          payload.removeFromLists.map(listId => {
            return put(
              removeUserItemTagsSuccess({
                itemId: payload.itemId,
                action: ActionType.TrackedInList,
                string_value: listId,
                unique: true,
              }),
            );
          }),
        );

        ReactGA.event({
          category: 'User',
          action: 'Updated list',
        });
      }
    } else {
    }
  });
};
