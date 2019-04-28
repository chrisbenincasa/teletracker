import { put, select, takeLatest } from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
import { FSA } from 'flux-standard-action';
import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
} from '../constants/user';
import { AppState } from '../reducers';
import { User } from '../types';
import { DataResponse, TeletrackerApi } from '../utils/api-client';
import { clientEffect, createAction } from './utils';

interface UserSelfRetrieveInitiatedPayload {
  force: boolean;
}

export type UserSelfRetrieveInitiatedAction = FSA<
  typeof USER_SELF_RETRIEVE_INITIATED,
  UserSelfRetrieveInitiatedPayload
>;

export type UserSelfRetrieveSuccessAction = FSA<
  typeof USER_SELF_RETRIEVE_SUCCESS,
  User
>;

export type UserActionTypes =
  | UserSelfRetrieveInitiatedAction
  | UserSelfRetrieveSuccessAction;

export const RetrieveUserSelfInitiated = createAction<
  UserSelfRetrieveInitiatedAction
>(USER_SELF_RETRIEVE_INITIATED);

export const RetrieveUserSelfSuccess = createAction<
  UserSelfRetrieveSuccessAction
>(USER_SELF_RETRIEVE_SUCCESS);

export const retrieveUserSaga = function*() {
  yield takeLatest(USER_SELF_RETRIEVE_INITIATED, function*({
    payload: { force } = { force: false },
  }: UserSelfRetrieveInitiatedAction) {
    let currState: AppState = yield select();

    if (currState.userSelf.self && !force) {
      yield put(RetrieveUserSelfSuccess(currState.userSelf.self));
    } else {
      try {
        let response: ApiResponse<DataResponse<User>> = yield clientEffect(
          client => client.getUserSelf,
        );

        if (response.ok && response.data) {
          yield put(RetrieveUserSelfSuccess(response.data!.data));
        } else {
          // TODO handle error
        }
      } catch (e) {
        // TODO handle error
      }
    }
  });
};

export const retrieveUser = (force: boolean = false) => {
  return RetrieveUserSelfInitiated({ force });
};
