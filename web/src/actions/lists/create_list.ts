import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';
import { FSA } from 'flux-standard-action';
import { retrieveAllLists } from './retrieve_all_lists';
import { ListRules } from '../../types';
export const USER_SELF_CREATE_LIST = 'user/self/create_list/INITIATED';
export const USER_SELF_CREATE_LIST_SUCCESS = 'user/self/create_list/SUCCESS';

export interface UserCreateListPayload {
  name: string;
  thingIds?: string[];
  rules?: ListRules;
}

export type UserCreateListAction = FSA<
  typeof USER_SELF_CREATE_LIST,
  UserCreateListPayload
>;

export type UserCreateListSuccessAction = FSA<
  typeof USER_SELF_CREATE_LIST_SUCCESS,
  { id: number }
>;

export const createList = createAction<UserCreateListAction>(
  USER_SELF_CREATE_LIST,
);

export const createListSuccess = createAction<UserCreateListSuccessAction>(
  USER_SELF_CREATE_LIST_SUCCESS,
);

export const createNewListSaga = function*() {
  yield takeEvery(USER_SELF_CREATE_LIST, function*({
    payload,
  }: UserCreateListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.createList,
        payload.name,
      );

      if (response.ok) {
        yield put(createListSuccess(response.data!.data));
        yield put(retrieveAllLists({}));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};
