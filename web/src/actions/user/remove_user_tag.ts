import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FSA } from 'flux-standard-action';
import { UserUpdateItemTagsPayload } from './update_user_tags';

export const USER_SELF_REMOVE_ITEM_TAGS =
  'user/self/remove_item_tags/INITIATED';
export const USER_SELF_REMOVE_ITEM_TAGS_SUCCESS =
  'user/self/remove_item_tags/SUCCESS';

export type UserRemoveItemTagsAction = FSA<
  typeof USER_SELF_REMOVE_ITEM_TAGS,
  UserUpdateItemTagsPayload
>;

export type UserRemoveItemTagsSuccessAction = FSA<
  typeof USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  UserUpdateItemTagsPayload
>;

export const removeUserItemTags = createAction<UserRemoveItemTagsAction>(
  USER_SELF_REMOVE_ITEM_TAGS,
);

export const removeUserItemTagsSuccess = createAction<
  UserRemoveItemTagsSuccessAction
>(USER_SELF_REMOVE_ITEM_TAGS_SUCCESS);

export const removeUserActionSaga = function*() {
  yield takeEvery(USER_SELF_REMOVE_ITEM_TAGS, function*({
    payload,
  }: UserRemoveItemTagsAction) {
    if (payload) {
      if (!payload.lazy) {
        yield put(removeUserItemTagsSuccess(payload));
      }

      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.removeActions,
        payload.thingId,
        payload.action,
      );

      if (response.ok && payload.lazy) {
        yield put(removeUserItemTagsSuccess(payload));
      } else {
        // TODO: Error
      }
    } else {
      // TODO: Error
    }
  });
};
