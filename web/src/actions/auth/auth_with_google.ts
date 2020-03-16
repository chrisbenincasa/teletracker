import { call, takeLatest } from '@redux-saga/core/effects';
import { createAction, createBasicAction } from '../utils';
import { FSA } from 'flux-standard-action';
import Auth, { CognitoHostedUIIdentityProvider } from '@aws-amplify/auth';
import { LoginState } from './login_action';

export const SIGNUP_GOOGLE_INITIATED = 'signup/google/INITIATED';
export const LOGIN_GOOGLE_INITIATED = 'login/google/INITIATED';

export interface LoginWithGooglePayload {
  state?: LoginState;
}

export type SignUpWithGoogleInitiatedAction = FSA<
  typeof SIGNUP_GOOGLE_INITIATED
>;

export const SignUpWithGoogleInitiated = createBasicAction<
  SignUpWithGoogleInitiatedAction
>(SIGNUP_GOOGLE_INITIATED);

export const signUpWithGoogle = () => SignUpWithGoogleInitiated();

export type LogInWithGoogleInitiatedAction = FSA<
  typeof LOGIN_GOOGLE_INITIATED,
  LoginWithGooglePayload
>;

export const LogInWithGoogleInitiated = createAction<
  LogInWithGoogleInitiatedAction
>(LOGIN_GOOGLE_INITIATED);

export const logInWithGoogle = (payload?: LoginWithGooglePayload) =>
  LogInWithGoogleInitiated(payload);

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

        console.log(creds);
      } catch (e) {
        console.error(e);
      }
    },
  );
};
