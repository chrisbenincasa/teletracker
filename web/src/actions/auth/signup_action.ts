import * as firebase from 'firebase/app';
import { call, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import ReactGA from 'react-ga';

export const SIGNUP_INITIATED = 'signup/INITIATED';
export const SIGNUP_SUCCESSFUL = 'signup/SUCCESSFUL';

interface SignupPayload {
  email: string;
  password: string;
  username: string;
}

export type SignupInitiatedAction = FSA<typeof SIGNUP_INITIATED, SignupPayload>;
export type SignupSuccessfulAction = FSA<typeof SIGNUP_SUCCESSFUL, string>;

export const SignupInitiated = createAction<SignupInitiatedAction>(
  SIGNUP_INITIATED,
);

export const SignupSuccessful = createAction<SignupSuccessfulAction>(
  SIGNUP_SUCCESSFUL,
);

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

        ReactGA.event({
          category: 'User',
          action: 'Signup',
        });
      } catch (e) {
        console.error(e);
      }
    } else {
    }
  });
};
