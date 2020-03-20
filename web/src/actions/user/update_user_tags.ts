import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FSA } from 'flux-standard-action';
import { ActionType } from '../../types';
import ReactGA from 'react-ga';

export const USER_SELF_UPDATE_ITEM_TAGS =
  'user/self/update_item_tags/INITIATED';
export const USER_SELF_UPDATE_ITEM_TAGS_SUCCESS =
  'user/self/update_item_tags/SUCCESS';

export interface UserUpdateItemTagsPayload {
  itemId: string;
  action: ActionType;
  value?: number;
  lazy?: boolean; // If true, requires the server call to complete before updating state.
  string_value?: string;
}

export type UserUpdateItemTagsAction = FSA<
  typeof USER_SELF_UPDATE_ITEM_TAGS,
  UserUpdateItemTagsPayload
>;

export type UserUpdateItemTagsSuccessAction = FSA<
  typeof USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  UserUpdateItemTagsPayload
>;

export const updateUserItemTags = createAction<UserUpdateItemTagsAction>(
  USER_SELF_UPDATE_ITEM_TAGS,
);

export const updateUserItemTagsSuccess = createAction<
  UserUpdateItemTagsSuccessAction
>(USER_SELF_UPDATE_ITEM_TAGS_SUCCESS);

export const updateUserActionSaga = function*() {
  yield takeEvery(USER_SELF_UPDATE_ITEM_TAGS, function*({
    payload,
  }: UserUpdateItemTagsAction) {
    if (payload) {
      if (!payload.lazy) {
        yield put(updateUserItemTagsSuccess(payload));
      }

      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateActions,
        payload.itemId,
        payload.action,
        payload.value,
      );

      ReactGA.event({
        category: 'User',
        action: 'Updated user tags',
      });

      if (response.ok && payload.lazy) {
        yield put(updateUserItemTagsSuccess(payload));
      } else {
        // TODO: Error
      }
    } else {
      // TODO: Error
    }
  });
};
