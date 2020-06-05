import { all, call, put, takeLatest } from '@redux-saga/core/effects';
import { logEvent, logException } from '../../utils/analytics';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { createAction } from '@reduxjs/toolkit';
import { withPayloadType } from '../utils';
import { Action } from 'redux-actions';

export const LOGIN_INITIATED = 'login/INITIATED';
export const LOGIN_SUCCESSFUL = 'login/SUCCESSFUL';

export interface LoginPayload {
  readonly email: string;
  readonly password: string;
}

export interface LoginRedirect {
  readonly route: string;
  readonly asPath: string;
  readonly query: object;
}

export interface LoginState {
  readonly redirect?: LoginRedirect;
}

export const loginInitiated = createAction(
  LOGIN_INITIATED,
  withPayloadType<LoginPayload>(),
);

export const loginSuccessful = createAction(
  LOGIN_SUCCESSFUL,
  withPayloadType<string>(),
);

/**
 * Saga responsible for handling the login flow
 */
export const loginSaga = function*() {
  yield takeLatest(LOGIN_INITIATED, function*(action: Action<any>) {
    if (loginInitiated.match(action) && action.payload) {
      try {
        let user: CognitoUser = yield call(
          (email: string, password: string) =>
            Auth.signIn({
              username: email,
              password,
            }),
          action.payload.email,
          action.payload.password,
        );

        yield all([
          put(
            loginSuccessful(
              user
                .getSignInUserSession()!
                .getAccessToken()
                .getJwtToken(),
            ),
          ),
          call(logEvent, 'Login and Signup', 'Login', 'Manual'),
        ]);
      } catch (e) {
        console.error(e);
        call(logException, `${e}`, false);
      }
    } else {
    }
  });
};

/**
 * Create a new LoginInitiated action, which when dispatched starts the
 * loginSaga
 * @param email
 * @param password
 */
export const login = (email: string, password: string) => {
  return loginInitiated({ email, password });
};
