import { call, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import Auth, { CognitoHostedUIIdentityProvider } from '@aws-amplify/auth';
import { LoginState } from './login_action';
import { createAction } from '@reduxjs/toolkit';
import { withPayloadType } from '../utils';
import { logEvent, logException } from '../../utils/analytics';

export const SIGNUP_GOOGLE_INITIATED = 'signup/google/INITIATED';
export const LOGIN_GOOGLE_INITIATED = 'login/google/INITIATED';

export interface LoginWithGooglePayload {
  state?: LoginState;
}

export const signUpWithGoogle = createAction(SIGNUP_GOOGLE_INITIATED);

export type LogInWithGoogleInitiatedAction = FSA<
  typeof LOGIN_GOOGLE_INITIATED,
  LoginWithGooglePayload
>;

export const logInWithGoogle = createAction(
  LOGIN_GOOGLE_INITIATED,
  withPayloadType<LoginWithGooglePayload>(),
);

export const authWithGoogleSaga = function*() {
  yield takeLatest(
    [SIGNUP_GOOGLE_INITIATED, LOGIN_GOOGLE_INITIATED],
    function*({ payload }: LogInWithGoogleInitiatedAction) {
      try {
        let creds = yield call(() => {
          let state;
          if (payload) {
            state = btoa(JSON.stringify(payload.state));
          }
          return Auth.federatedSignIn({
            provider: CognitoHostedUIIdentityProvider.Google,
            customState: state,
          });
        });

        // todo:  separate each
        yield call(logEvent, 'Login and Signup', 'Login/Signup', 'Google');
        console.log(creds);
      } catch (e) {
        console.error(e);
        yield call(logException, `${e}`, false);
      }
    },
  );
};
