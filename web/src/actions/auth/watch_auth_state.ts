import { call, delay, put, take } from '@redux-saga/core/effects';
import TeletrackerApi from '../../utils/api-client';
import { END, eventChannel } from '@redux-saga/core';
import { SetToken, UnsetToken } from './set_token_action';
import { LogoutSuccessful } from './logout_action';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { Hub } from '@aws-amplify/core';
import { LoginSuccessful } from './login_action';
import { UserStateChange } from './index';

// TODO: This is really close to getUserSelf and a lot of other logic...
// maybe we should factor this out somehow?
export const initialAuthState = function*() {
  try {
    let user: CognitoUser = yield call([Auth, Auth.currentAuthenticatedUser], {
      bypassCache: false,
    });

    yield put(UserStateChange(user));

    let signInUserSession = user.getSignInUserSession();
    if (signInUserSession) {
      let token = signInUserSession.getAccessToken().getJwtToken();
      yield call([TeletrackerApi, TeletrackerApi.setToken], token);
      yield put(SetToken(token));
    }
  } catch (e) {
    console.error(e);
    yield put(UserStateChange(undefined));
    yield put(UnsetToken());
    yield put(LogoutSuccessful());
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
            LoginSuccessful(
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
    }
  }
};

export const authStateChannelMaker = function*() {
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
