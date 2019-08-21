import { call, takeLatest } from '@redux-saga/core/effects';
import * as firebase from 'firebase/app';
import { createBasicAction } from '../utils';
import { FSA } from 'flux-standard-action';

export const SIGNUP_GOOGLE_INITIATED = 'signup/google/INITIATED';
export const LOGIN_GOOGLE_INITIATED = 'login/google/INITIATED';

export type SignUpWithGoogleInitiatedAction = FSA<
  typeof SIGNUP_GOOGLE_INITIATED
>;

export const SignUpWithGoogleInitiated = createBasicAction<
  SignUpWithGoogleInitiatedAction
>(SIGNUP_GOOGLE_INITIATED);

export const signUpWithGoogle = () => SignUpWithGoogleInitiated();

export type LogInWithGoogleInitiatedAction = FSA<typeof LOGIN_GOOGLE_INITIATED>;

export const LogInWithGoogleInitiated = createBasicAction<
  LogInWithGoogleInitiatedAction
>(LOGIN_GOOGLE_INITIATED);

export const logInWithGoogle = () => LogInWithGoogleInitiated();

export const authWithGoogleSaga = function*() {
  yield takeLatest(
    [SIGNUP_GOOGLE_INITIATED, LOGIN_GOOGLE_INITIATED],
    function*() {
      let googleAuthProvider = new firebase.auth.GoogleAuthProvider();

      try {
        yield call(() =>
          firebase.auth().signInWithRedirect(googleAuthProvider),
        );
      } catch (e) {
        console.error(e);
      }
    },
  );
};
