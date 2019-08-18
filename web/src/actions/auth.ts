import * as firebase from 'firebase/app';
import 'firebase/auth';
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
  SET_TOKEN,
  SIGNUP_INITIATED,
  SIGNUP_SUCCESSFUL,
  UNSET_TOKEN,
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

interface SignupPayload {
  email: string;
  password: string;
  username: string;
}

export type AuthCheckInitiatedAction = FSA<typeof AUTH_CHECK_INITIATED>;
export type AuthCheckAuthorizedAction = FSA<typeof AUTH_CHECK_AUTHORIZED>;
export type AuthCheckUnauthorizedAction = FSA<typeof AUTH_CHECK_UNAUTH>;
export type AuthCheckFailedAction = FSA<typeof AUTH_CHECK_FAILED, Error>;
export type LoginInitiatedAction = FSA<typeof LOGIN_INITIATED, LoginPayload>;
export type LoginSuccessfulAction = FSA<typeof LOGIN_SUCCESSFUL, string>;
export type LogoutInitiatedAction = FSA<typeof LOGOUT_INITIATED>;
export type LogoutSuccessfulAction = FSA<typeof LOGOUT_SUCCESSFUL>;
export type SignupInitiatedAction = FSA<typeof SIGNUP_INITIATED, SignupPayload>;
export type SignupSuccessfulAction = FSA<typeof SIGNUP_SUCCESSFUL, string>;
export type SetTokenAction = FSA<typeof SET_TOKEN, string>;
export type UnsetTokenAction = FSA<typeof UNSET_TOKEN>;

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

export const SignupInitiated = createAction<SignupInitiatedAction>(
  SIGNUP_INITIATED,
);

export const SignupSuccessful = createAction<SignupSuccessfulAction>(
  SIGNUP_SUCCESSFUL,
);

export const SetToken = createAction<SetTokenAction>(SET_TOKEN);
export const UnsetToken = createBasicAction<UnsetTokenAction>(UNSET_TOKEN);

export type AuthActionTypes =
  | AuthCheckInitiatedAction
  | AuthCheckAuthorizedAction
  | AuthCheckUnauthorizedAction
  | AuthCheckFailedAction
  | LoginInitiatedAction
  | LoginSuccessfulAction
  | LogoutSuccessfulAction
  | SignupInitiatedAction
  | SignupSuccessfulAction;

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

      console.log(response);

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
      if (currState.auth && currState.auth.token) {
        client.setToken(currState.auth.token);
      }

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

/**
 * Function that returns a new AuthCheckInitiated action, which, when dispatched,
 * runs the checkAuthSaga
 */
export const checkAuth = () => {
  return AuthCheckInitiated();
};

/**
 * Saga responsible for handling the signup flow
 */
export const signupSaga = function*() {
  yield takeLatest(SIGNUP_INITIATED, function*({
    payload,
  }: SignupInitiatedAction) {
    if (payload) {
      try {
        yield call(
          (email: string, password: string) =>
            firebase.auth().createUserWithEmailAndPassword(email, password),
          payload.email,
          payload.password,
        );
      } catch (e) {
        console.error(e);
      }
    } else {
    }
  });
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
        yield call(() =>
          firebase.auth().setPersistence(firebase.auth.Auth.Persistence.LOCAL),
        );

        yield call(
          (email: string, password: string) =>
            firebase.auth().signInWithEmailAndPassword(email, password),
          payload.email,
          payload.password,
        );

        let token: string = yield call(() =>
          firebase.auth().currentUser!.getIdToken(),
        );

        yield put(LoginSuccessful(token));
        // }
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
      yield call(() => firebase.auth().signOut());
      yield put(LogoutSuccessful());
    } catch (e) {}
  });
};

/**
 * Create a new SignupInitiated action, which when dispatched starts the
 * signupSaga
 * @param username
 * @param email
 * @param password
 */
export const signup = (username: string, email: string, password: string) => {
  return SignupInitiated({ username, email, password });
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
