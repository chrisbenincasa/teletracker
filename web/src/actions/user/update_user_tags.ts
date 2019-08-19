import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { FSA } from 'flux-standard-action';
import { ActionType } from '../../types';

export const USER_SELF_UPDATE_ITEM_TAGS =
  'user/self/update_item_tags/INITIATED';
export const USER_SELF_UPDATE_ITEM_TAGS_SUCCESS =
  'user/self/update_item_tags/SUCCESS';

export interface UserUpdateItemTagsPayload {
  thingId: string;
  action: ActionType;
  value?: number;
  lazy?: boolean; // If true, requires the server call to complete before updating state.
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
        payload.thingId,
        payload.action,
        payload.value,
      );

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
