import { call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import ReactGA from 'react-ga';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { getUserSelf, RetrieveUserSelfInitiated } from '../user';

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
          LoginSuccessful(
            user
              .getSignInUserSession()!
              .getAccessToken()
              .getJwtToken(),
          ),
        );

        ReactGA.event({
          category: 'User',
          action: 'Login',
        });

        // yield put(getUserSelf());
      } catch (e) {
        console.error(e);
      }
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
