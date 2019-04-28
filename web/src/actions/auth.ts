import {
  call,
  put,
  select,
  takeLatest,
  takeLeading,
} from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
import { FSA } from 'flux-standard-action';
import {
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_UNAUTH,
  LOGIN_INITIATED,
  LOGIN_SUCCESSFUL,
  LOGOUT_INITIATED,
  LOGOUT_SUCCESSFUL,
} from '../constants/auth';
import { AppState } from '../reducers';
import { TeletrackerApi } from '../utils/api-client';
import {
  checkOrSetToken,
  clientEffect,
  createAction,
  createBasicAction,
} from './utils';

const client = TeletrackerApi.instance;

interface LoginPayload {
  email: string;
  password: string;
}

export type AuthCheckInitiatedAction = FSA<typeof AUTH_CHECK_INITIATED>;
export type AuthCheckAuthorizedAction = FSA<typeof AUTH_CHECK_AUTHORIZED>;
export type AuthCheckUnauthorizedAction = FSA<typeof AUTH_CHECK_UNAUTH>;
export type AuthCheckFailedAction = FSA<typeof AUTH_CHECK_FAILED, Error>;
export type LoginInitiatedAction = FSA<typeof LOGIN_INITIATED, LoginPayload>;
export type LoginSuccessfulAction = FSA<typeof LOGIN_SUCCESSFUL, string>;
export type LogoutInitiatedAction = FSA<typeof LOGOUT_INITIATED>;
export type LogoutSuccessfulAction = FSA<typeof LOGOUT_SUCCESSFUL>;

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

export const LoginInitiated = createAction<LoginInitiatedAction>(
  LOGIN_INITIATED,
);

export const LoginSuccessful = createAction<LoginSuccessfulAction>(
  LOGIN_SUCCESSFUL,
);

export const LogoutInitiated = createBasicAction<LogoutInitiatedAction>(
  LOGOUT_INITIATED,
);

export const LogoutSuccessful = createBasicAction<LogoutSuccessfulAction>(
  LOGOUT_SUCCESSFUL,
);

export type AuthActionTypes =
  | AuthCheckInitiatedAction
  | AuthCheckAuthorizedAction
  | AuthCheckUnauthorizedAction
  | AuthCheckFailedAction
  | LoginInitiatedAction
  | LoginSuccessfulAction
  | LogoutSuccessfulAction;

/**
 * A saga that checks the authorization state of the current token in
 * state.
 */
export const checkAuthSaga = function*() {
  yield takeLeading(AUTH_CHECK_INITIATED, function*() {
    let result = yield checkOrSetToken();

    if (result.token) {
      let currState: AppState = yield select();

      // If the current state already has a token available, set it here
      if (currState.auth && currState.auth.token) {
        client.setToken(currState.auth.token);
      }

      // Check the current token's auth status
      try {
        let response: ApiResponse<any> = yield call({
          context: client,
          fn: client.getAuthStatus,
        });

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

/**
 * Function that returns a new AuthCheckInitiated action, which, when dispatched,
 * runs the checkAuthSaga
 */
export const checkAuth = () => {
  return AuthCheckInitiated();
};

/**
 * Saga responsible for handling the login flow
 */
export const loginSaga = function*() {
  yield takeLatest(LOGIN_INITIATED, function*({
    payload,
  }: LoginInitiatedAction) {
    if (payload) {
      try {
        // Call out to the Teletracker API to attempt to login the user
        let response: ApiResponse<any> = yield clientEffect(
          client => client.loginUser,
          payload.email,
          payload.password,
        );

        if (response.ok) {
          let token = response.data.data.token;
          // Sets the token on the "global" TeletrackerApi instance. This is required
          // when doing auth-gated calls
          client.setToken(token);

          yield put(LoginSuccessful(token));
        }
      } catch (e) {}
    } else {
    }
  });
};

/**
 * Saga that handles the logout flow.
 */
export const logoutSaga = function*() {
  yield takeLatest(LOGOUT_INITIATED, function*() {
    try {
      let response: ApiResponse<any> = yield clientEffect(
        client => client.logoutUser,
      );

      if (response.ok) {
        // Clear the token from the global client. Auth-gated calls will fail now
        client.clearToken();

        yield put(LogoutSuccessful());
      }
    } catch (e) {}
  });
};

/**
 * Create a new LoginInitiated action, which when dispatched starts the
 * loginSaga
 * @param email
 * @param password
 */
export const login = (email: string, password: string) => {
  return LoginInitiated({ email, password });
};

/**
 * Create a new LogoutInitiated, which when dispatched starts the
 * logoutSaga
 */
export const logout = () => {
  return LogoutInitiated();
};
