import { call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { logEvent } from '../../utils/analytics';
import Auth, { CognitoUser } from '@aws-amplify/auth';

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

        logEvent('User', 'Signup');

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
          SignupSuccessful(
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
