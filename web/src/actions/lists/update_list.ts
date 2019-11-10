import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { RetrieveUserSelfInitiated } from '../user';
import { FSA } from 'flux-standard-action';
import { ListRules, ListOptions } from '../../types';
import { ListRetrieveInitiated } from './get_list';
import ReactGA from 'react-ga';

export const USER_SELF_UPDATE_LIST = 'user/self/update_list/INITIATED';
export const USER_SELF_UPDATE_LIST_SUCCESS = 'user/self/update_list/SUCCESS';

export interface UserUpdateListPayload {
  listId: number;
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
          yield put(
            ListRetrieveInitiated({ listId: payload.listId, force: true }),
          );
        }

        yield put(
          updateListSuccess({
            listId: payload.listId,
            name: payload.name,
            rules: payload.rules,
            options: payload.options,
          }),
        );

        yield put(RetrieveUserSelfInitiated({ force: true }));

        ReactGA.event({
          category: 'User',
          action: 'Renamed list',
        });
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};
