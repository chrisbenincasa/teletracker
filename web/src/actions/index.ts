import { all, put, take } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { REHYDRATE } from 'redux-persist';
import { STARTUP } from '../constants';
import { checkAuthSaga, loginSaga, logoutSaga, signupSaga } from './auth';
import {
  addToListSaga,
  retrieveListSaga,
  retrieveListsSaga,
  updateListSaga,
} from './lists';
import { loadNetworksSaga } from './metadata';
import { searchSaga } from './search';
import {
  createNewListSaga,
  deleteListSaga,
  removeUserActionSaga,
  renameListSaga,
  retrieveUserSaga,
  updateNetworksForUserSaga,
  updateUserActionSaga,
  updateUserPreferencesSaga,
  updateUserSaga,
  watchAuthState,
} from './user';
import { createBasicAction } from './utils';
import { allAvailabilitySaga, upcomingAvailabilitySaga } from './availability';
import { fetchItemDetailsBatchSaga, fetchItemDetailsSaga } from './item-detail';

type StartupAction = FSA<typeof STARTUP>;

const StartupAction = createBasicAction<StartupAction>(STARTUP);

function* startupSaga() {
  yield put(StartupAction());
}

export function* root() {
  // Wait until persisted state is rehydrated
  yield take(REHYDRATE);

  // Start all of the sagas
  yield all([
    startupSaga(),
    watchAuthState(),
    checkAuthSaga(),
    retrieveListSaga(),
    retrieveListsSaga(),
    addToListSaga(),
    searchSaga(),
    loginSaga(),
    logoutSaga(),
    signupSaga(),
    retrieveUserSaga(),
    updateListSaga(),
    loadNetworksSaga(),
    updateNetworksForUserSaga(),
    updateUserPreferencesSaga(),
    updateUserSaga(),
    createNewListSaga(),
    deleteListSaga(),
    renameListSaga(),
    updateUserActionSaga(),
    removeUserActionSaga(),
    upcomingAvailabilitySaga(),
    fetchItemDetailsSaga(),
    fetchItemDetailsBatchSaga(),
    allAvailabilitySaga(),
  ]);
}
