import { call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import ReactGA from 'react-ga';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { createAction } from '@reduxjs/toolkit';
import { withPayloadType } from '../utils';

export const SIGNUP_INITIATED = 'signup/INITIATED';
export const SIGNUP_SUCCESSFUL = 'signup/SUCCESSFUL';

interface SignupPayload {
  email: string;
  password: string;
  username: string;
}

export type SignupInitiatedAction = FSA<typeof SIGNUP_INITIATED, SignupPayload>;
export type SignupSuccessfulAction = FSA<typeof SIGNUP_SUCCESSFUL, string>;

export const signupInitiated = createAction(
  SIGNUP_INITIATED,
  withPayloadType<SignupPayload>(),
);

export const signupSuccessful = createAction(
  SIGNUP_SUCCESSFUL,
  withPayloadType<string>(),
);

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
          (email: string, password: string) => {
            return Auth.signUp({
              username: email,
              password,
              attributes: {
                email,
              },
            });
          },
          payload.email,
          payload.password,
        );

        ReactGA.event({
          category: 'User',
          action: 'Signup',
        });

        let user: CognitoUser = yield call(
          (email: string, password: string) =>
            Auth.signIn({
              username: email,
              password,
            }),
          payload.email,
          payload.password,
        );

        yield put(
          signupSuccessful(
            user
              .getSignInUserSession()!
              .getAccessToken()
              .getJwtToken(),
          ),
        );
      } catch (e) {
        console.error(e);
      }
    } else {
    }
  });
};
