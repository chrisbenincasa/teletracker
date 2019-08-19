import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { RetrieveUserSelfInitiated } from '../user';
import { FSA } from 'flux-standard-action';

export const USER_SELF_RENAME_LIST = 'user/self/rename_list/INITIATED';
export const USER_SELF_RENAME_LIST_SUCCESS = 'user/self/rename_list/SUCCESS';

export interface UserRenameListPayload {
  listId: number;
  listName: string;
}

export type UserRenameListAction = FSA<
  typeof USER_SELF_RENAME_LIST,
  UserRenameListPayload
>;

export type UserRenameListSuccessAction = FSA<
  typeof USER_SELF_RENAME_LIST_SUCCESS,
  UserRenameListPayload
>;

export const renameList = createAction<UserRenameListAction>(
  USER_SELF_RENAME_LIST,
);

export const renameListSuccess = createAction<UserRenameListSuccessAction>(
  USER_SELF_RENAME_LIST_SUCCESS,
);

export const renameListSaga = function*() {
  yield takeEvery(USER_SELF_RENAME_LIST, function*({
    payload,
  }: UserRenameListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.renameList,
        payload.listId,
        payload.listName,
      );

      if (response.ok) {
        yield put(
          renameListSuccess({
            listId: payload.listId,
            listName: payload.listName,
          }),
        );
        yield put(RetrieveUserSelfInitiated({ force: true }));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};
