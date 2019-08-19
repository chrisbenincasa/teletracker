import { call, put, take } from '@redux-saga/core/effects';
import TeletrackerApi from '../../utils/api-client';
import { END, eventChannel } from '@redux-saga/core';
import * as firebase from 'firebase';
import { UnsetToken } from './set_token_action';

export const watchAuthState = function*() {
  const authStateChannel = yield call(authStateChannelMaker);
  try {
    while (true) {
      let { user } = yield take(authStateChannel);
      if (user) {
        let token: string = yield call(() => user.getIdToken());
        yield call([TeletrackerApi, TeletrackerApi.setToken], token);
        yield put({ type: 'auth/SET_TOKEN', payload: token });
        yield put({ type: 'USER_STATE_CHANGE', payload: user });
      } else {
        yield put(UnsetToken());
      }
    }
  } catch (e) {
    console.error(e);
  }
};

export const authStateChannelMaker = function*() {
  return eventChannel(emitter => {
    return firebase.auth().onAuthStateChanged(
      user => {
        emitter({ user });
      },
      err => {
        console.error(err);
      },
      () => {
        emitter(END);
      },
    );
  });
};
