import * as firebase from 'firebase/app';
import 'firebase/auth';
import { call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';

export const LOGIN_INITIATED = 'login/INITIATED';
export const LOGIN_SUCCESSFUL = 'login/SUCCESSFUL';

interface LoginPayload {
  email: string;
  password: string;
}

export type LoginInitiatedAction = FSA<typeof LOGIN_INITIATED, LoginPayload>;
export type LoginSuccessfulAction = FSA<typeof LOGIN_SUCCESSFUL, string>;

export const LoginInitiated = createAction<LoginInitiatedAction>(
  LOGIN_INITIATED,
);

export const LoginSuccessful = createAction<LoginSuccessfulAction>(
  LOGIN_SUCCESSFUL,
);

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
 * Create a new LoginInitiated action, which when dispatched starts the
 * loginSaga
 * @param email
 * @param password
 */
export const login = (email: string, password: string) => {
  return LoginInitiated({ email, password });
};
