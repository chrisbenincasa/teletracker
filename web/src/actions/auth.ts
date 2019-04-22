import { call, put, select, takeLatest } from '@redux-saga/core/effects';
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
  clientEffect2,
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

export const checkAuthSaga = function*() {
  yield takeLatest(AUTH_CHECK_INITIATED, function*() {
    let r = yield checkOrSetToken();

    if (r.token) {
      let currState: AppState = yield select();
      if (currState.auth && currState.auth.token) {
        client.setToken(currState.auth.token);
        yield put(AuthCheckAuthorized());
      } else {
        try {
          let response: ApiResponse<any> = yield call({
            context: client,
            fn: client.getAuthStatus,
          });

          if (response.status == 200) {
            yield put(AuthCheckAuthorized());
          } else {
            yield put(AuthCheckUnauthorized());
          }
        } catch (e) {
          yield put(AuthCheckFailed(e));
        }
      }
    } else {
      // timeout scenario... what do!?
    }
  });
};

export const checkAuth = () => {
  return AuthCheckInitiated();
};

export const loginSaga = function*() {
  yield takeLatest(LOGIN_INITIATED, function*({
    payload,
  }: LoginInitiatedAction) {
    if (payload) {
      try {
        let response: ApiResponse<any> = yield clientEffect2(
          client => client.loginUser,
          payload.email,
          payload.password,
        );
        if (response.ok) {
          let token = response.data.data.token;
          client.setToken(token);
          yield put(LoginSuccessful(token));
        }
      } catch (e) {}
    } else {
    }
  });
};

export const logoutSaga = function*() {
  yield takeLatest(LOGOUT_INITIATED, function*() {
    try {
      let response: ApiResponse<any> = yield clientEffect2(
        client => client.logoutUser,
      );

      if (response.ok) {
        client.clearToken();
        yield put(LogoutSuccessful());
      }
    } catch (e) {}
  });
};

export const login = (email: string, password: string) => {
  return LoginInitiated({ email, password });
};

export const logout = () => {
  return LogoutInitiated();
};
