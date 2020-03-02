import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FSA } from 'flux-standard-action';
import { retrieveAllLists } from './retrieve_all_lists';
import ReactGA from 'react-ga';

export const USER_SELF_DELETE_LIST = 'user/self/delete_list/INITIATED';
export const USER_SELF_DELETE_LIST_SUCCESS = 'user/self/delete_list/SUCCESS';

export interface UserDeleteListPayload {
  listId: string;
  mergeListId?: string;
}

export type UserDeleteListAction = FSA<
  typeof USER_SELF_DELETE_LIST,
  UserDeleteListPayload
>;

export type UserDeleteListSuccessAction = FSA<
  typeof USER_SELF_DELETE_LIST_SUCCESS,
  UserDeleteListPayload
>;

export const deleteList = createAction<UserDeleteListAction>(
  USER_SELF_DELETE_LIST,
);

export const deleteListSuccess = createAction<UserDeleteListSuccessAction>(
  USER_SELF_DELETE_LIST_SUCCESS,
);

export const deleteListSaga = function*() {
  yield takeEvery(USER_SELF_DELETE_LIST, function*({
    payload,
  }: UserDeleteListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.deleteList,
        payload.listId,
        payload.mergeListId ? payload.mergeListId : undefined,
      );

      if (response.ok) {
        yield put(
          deleteListSuccess({
            listId: payload.listId,
            mergeListId: payload.mergeListId,
          }),
        );
        yield put(retrieveAllLists({}));

        ReactGA.event({
          category: 'User',
          action: 'Deleted list',
        });
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};
