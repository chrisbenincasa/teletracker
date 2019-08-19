import 'firebase/auth';
import { put, select, takeLeading } from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
// import { AUTH_CHECK_INITIATED } from '../constants/auth';
import { AppState } from '../../reducers';
import { checkOrSetToken, clientEffect } from '../utils';
import { FSA } from 'flux-standard-action';
// import {AUTH_CHECK_AUTHORIZED, AUTH_CHECK_FAILED, AUTH_CHECK_UNAUTH} from "../../constants/auth";
import { createAction, createBasicAction } from '../utils';

export const AUTH_CHECK = 'auth/CHECK';
export const AUTH_CHECK_INITIATED = 'auth/CHECK_INITIATED';
export const AUTH_CHECK_AUTHORIZED = 'auth/IS_AUTHED';
export const AUTH_CHECK_UNAUTH = 'auth/NOT_AUTHED';
export const AUTH_CHECK_FAILED = 'auth/CHECK_FAILED';

export type AuthCheckInitiatedAction = FSA<typeof AUTH_CHECK_INITIATED>;
export type AuthCheckAuthorizedAction = FSA<typeof AUTH_CHECK_AUTHORIZED>;
export type AuthCheckUnauthorizedAction = FSA<typeof AUTH_CHECK_UNAUTH>;
export type AuthCheckFailedAction = FSA<typeof AUTH_CHECK_FAILED, Error>;

export const AuthCheckInitiated = createBasicAction<AuthCheckInitiatedAction>(
  AUTH_CHECK_INITIATED,
);

export const AuthCheckAuthorized = createBasicAction<AuthCheckAuthorizedAction>(
  AUTH_CHECK_AUTHORIZED,
);

export const AuthCheckUnauthorized = createBasicAction<
  AuthCheckUnauthorizedAction
>(AUTH_CHECK_UNAUTH);

export const AuthCheckFailed = createAction<AuthCheckFailedAction>(
  AUTH_CHECK_FAILED,
);

/**
 * A saga that checks the authorization state of the current token in
 * state.
 */
export const checkAuthSaga = function*() {
  yield takeLeading(AUTH_CHECK_INITIATED, function*() {
    console.log('got check auth');
    try {
      let response: ApiResponse<any> = yield clientEffect(
        client => client.getAuthStatus,
      );

      if (response.status == 200) {
        yield put(AuthCheckAuthorized());
      } else if (response.status === 401) {
        yield put(AuthCheckUnauthorized());
      } else {
        yield put(
          AuthCheckFailed(
            new Error(
              `Got error code ${response.status} while checking auth status`,
            ),
          ),
        );
      }
    } catch (e) {
      yield put(AuthCheckFailed(e));
    }

    let result = yield checkOrSetToken();

    console.log(result);

    if (result.token) {
      let currState: AppState = yield select();

      // If the current state already has a token available, set it here
      // if (currState.auth && currState.auth.token) {
      //   client.setToken(currState.auth.token);
      // }

      // Check the current token's auth status
      try {
        let response: ApiResponse<any> = yield clientEffect(
          client => client.getAuthStatus,
        );

        if (response.status == 200) {
          yield put(AuthCheckAuthorized());
        } else if (response.status === 401) {
          yield put(AuthCheckUnauthorized());
        } else {
          yield put(
            AuthCheckFailed(
              new Error(
                `Got error code ${response.status} while checking auth status`,
              ),
            ),
          );
        }
      } catch (e) {
        yield put(AuthCheckFailed(e));
      }
    } else {
      // timeout scenario... what do!?
    }
  });
};
