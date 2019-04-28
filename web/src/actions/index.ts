import { all, put, select, take, takeLeading } from '@redux-saga/core/effects';
import { Action, Dispatch } from 'redux';
import { REHYDRATE } from 'redux-persist';
import { ThunkAction } from 'redux-thunk';
import { STARTUP } from '../constants';
import { AppState } from '../reducers';
import { TeletrackerApi } from '../utils/api-client';
import {
  checkAuthSaga,
  loginSaga,
  logoutSaga,
  AuthCheckInitiated,
} from './auth';
import {
  retrieveListSaga,
  addToListSaga,
  retrieveListsSaga,
  updateListSaga,
} from './lists';
import { searchSaga } from './search';
import { retrieveUserSaga } from './user';
import { SET_TOKEN, TOKEN_SET } from '../constants/auth';
import { FSA } from 'flux-standard-action';
import { createBasicAction } from './utils';

type StartupAction = FSA<typeof STARTUP>;

const StartupAction = createBasicAction<StartupAction>(STARTUP);

export function* setToken() {
  // We use takeLeading here to ensure that if this action is in progress
  // and another SET_TOKEN action is dispatched, the new incoming action is
  // discarded and the original one continues to execute
  yield takeLeading(SET_TOKEN, function*() {
    let state: AppState = yield select();

    if (state.auth.token && !TeletrackerApi.instance.isTokenSet()) {
      TeletrackerApi.instance.setToken(state.auth.token);
    }
    yield put({ type: TOKEN_SET });
  });
}

function* startupSaga() {
  yield put(StartupAction());
  yield put(AuthCheckInitiated());
  yield put({ type: SET_TOKEN });
}

export function* root() {
  // Wait until persisted state is rehydrated
  yield take(REHYDRATE);

  // Start all of the sagas
  yield all([
    startupSaga(),
    setToken(),
    checkAuthSaga(),
    retrieveListSaga(),
    retrieveListsSaga(),
    addToListSaga(),
    searchSaga(),
    loginSaga(),
    logoutSaga(),
    retrieveUserSaga(),
    updateListSaga(),
  ]);
}
