import { all, put, select, take, takeLeading } from '@redux-saga/core/effects';
import { Action, Dispatch } from 'redux';
import { REHYDRATE } from 'redux-persist';
import { ThunkAction } from 'redux-thunk';
import { STARTUP } from '../constants';
import { AppState } from '../reducers';
import { TeletrackerApi } from '../utils/api-client';
import { checkAuthSaga, loginSaga, logoutSaga } from './auth';
import { retrieveListSaga, addToListSaga } from './lists';
import { searchSaga } from './search';
import { retrieveUserSaga } from './user';

interface StartupAction {
  type: typeof STARTUP;
}

const startupAction: () => StartupAction = () => ({
  type: STARTUP,
});

const startup: () => ThunkAction<
  void,
  AppState,
  null,
  Action<StartupAction>
> = () => {
  return (dispatch: Dispatch, stateFn: () => AppState) => {
    dispatch(startupAction());
    let state = stateFn();

    if (state.auth.token) {
      TeletrackerApi.instance.setToken(state.auth.token);
    }
  };
};

export function* setToken() {
  yield takeLeading('SET_TOKEN', function*() {
    let state: AppState = yield select();

    if (state.auth.token && !TeletrackerApi.instance.isTokenSet()) {
      TeletrackerApi.instance.setToken(state.auth.token);
    }
    yield put({ type: 'TOKEN_SET' });
  });
}

function* startupSaga() {
  yield put(startupAction());
  yield put({ type: 'SET_TOKEN' });
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
    addToListSaga(),
    searchSaga(),
    loginSaga(),
    logoutSaga(),
    retrieveUserSaga(),
  ]);
}

export default startup;
