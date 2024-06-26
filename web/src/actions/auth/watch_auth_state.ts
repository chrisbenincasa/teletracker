import { call, delay, put, take } from '@redux-saga/core/effects';
import TeletrackerApi from '../../utils/api-client';
import { END, eventChannel } from '@redux-saga/core';
import { setToken, unsetToken } from './set_token_action';
import { logoutSuccessful } from './logout_action';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { Hub } from '@aws-amplify/core';
import { loginSuccessful } from './login_action';
import { userStateChange } from './index';
import { logException } from '../../utils/analytics';

// TODO: This is really close to getUserSelf and a lot of other logic...
// maybe we should factor this out somehow?
export const initialAuthState = function*() {
  try {
    let user: CognitoUser = yield call([Auth, Auth.currentAuthenticatedUser], {
      bypassCache: false,
    });

    yield put(
      userStateChange({ authenticated: true, userId: user.getUsername() }),
    );

    let signInUserSession = user.getSignInUserSession();
    if (signInUserSession) {
      let token = signInUserSession.getAccessToken().getJwtToken();
      yield call([TeletrackerApi, TeletrackerApi.setToken], token);
      yield put(setToken(token));
    }
  } catch (e) {
    yield put(userStateChange({ authenticated: false }));
    yield put(unsetToken());
    yield put(logoutSuccessful());
  }
};

export const authStateWatcher = function*() {
  const authStateChannel = yield call(authStateChannelMaker);
  while (true) {
    try {
      let event = yield take(authStateChannel);
      switch (event.type) {
        case 'auth/HUB_EVENT/signIn':
          let user: CognitoUser = event.payload;
          let session = user.getSignInUserSession();
          // This is a really horrible hack where Amplify doesn't immediately
          // set the user session when sending this event, but rather sets it
          // right _after_ the event. Generally, this loop will only need to
          // run once in order for Amplify to set this, but we should consider
          // putting a timeout in here.
          // TODO: File a bug on Amplify to fix this shitty code.
          while (!session) {
            session = yield delay(25, user.getSignInUserSession());
          }

          yield put(
            loginSuccessful(
              user
                .getSignInUserSession()!
                .getAccessToken()
                .getJwtToken(),
            ),
          );
          break;
        default:
          break;
      }
    } catch (e) {
      console.error(e);
      call(logException, `${e}`, false);
    }
  }
};

export const authStateChannelMaker = function() {
  return eventChannel(emitter => {
    // Listen to auth events from Amplify and forward them to a saga.
    Hub.listen('auth', ({ payload: { event, data } }) => {
      emitter({ type: `auth/HUB_EVENT/${event}`, payload: data });
    });

    return () =>
      Hub.remove('auth', () => {
        emitter(END);
      });
  });
};
