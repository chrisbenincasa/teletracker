import { all, call, put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { RetrieveUserSelfInitiated } from '../user';
import { FSA } from 'flux-standard-action';
import { ListRules, ListOptions } from '../../types';
import { getList } from './get_list';
import { logEvent } from '../../utils/analytics';

export const USER_SELF_UPDATE_LIST = 'user/self/update_list/INITIATED';
export const USER_SELF_UPDATE_LIST_SUCCESS = 'user/self/update_list/SUCCESS';

export interface UserUpdateListPayload {
  listId: string;
  name?: string;
  rules?: ListRules;
  options?: ListOptions;
}

export type UserUpdateListAction = FSA<
  typeof USER_SELF_UPDATE_LIST,
  UserUpdateListPayload
>;

export type UserUpdateListSuccessAction = FSA<
  typeof USER_SELF_UPDATE_LIST_SUCCESS,
  UserUpdateListPayload
>;

export const updateList = createAction<UserUpdateListAction>(
  USER_SELF_UPDATE_LIST,
);

export const updateListSuccess = createAction<UserUpdateListSuccessAction>(
  USER_SELF_UPDATE_LIST_SUCCESS,
);

export const updateListSaga = function*() {
  yield takeEvery(USER_SELF_UPDATE_LIST, function*({
    payload,
  }: UserUpdateListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateList,
        payload.listId,
        payload.name,
        payload.rules,
        payload.options,
      );

      if (response.ok) {
        // TODO add real type
        let requiresRefresh = response.data!.data.requiresRefresh;

        if (requiresRefresh) {
          yield put(getList({ listId: payload.listId }));
        }

        yield all([
          put(
            updateListSuccess({
              listId: payload.listId,
              name: payload.name,
              rules: payload.rules,
              options: payload.options,
            }),
          ),
          call(logEvent, 'User', 'Updated list'),
        ]);

        yield put(RetrieveUserSelfInitiated({ force: true }));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};
