import { all, fork, put, take } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { REHYDRATE } from 'redux-persist';
import {
  authWithGoogleSaga,
  checkAuthSaga,
  loginSaga,
  logoutSaga,
  signupSaga,
  watchAuthState,
} from './auth';
import {
  allAvailabilitySaga,
  networkAvailabilitySaga,
  upcomingAvailabilitySaga,
} from './availability';
import { fetchItemDetailsSaga } from './item-detail';
import {
  addToListSaga,
  createNewListSaga,
  deleteListSaga,
  retrieveListSaga,
  retrieveListsSaga,
  updateListSaga,
  updateListTrackingSaga,
} from './lists';
import { loadNetworks, loadNetworksSaga } from './metadata';
import { loadGenres, loadGenresSaga } from './metadata/load_genres';
import { fetchPersonDetailsSaga } from './people/get_person';
import { popularSaga } from './popular';
import { searchSaga } from './search';
import {
  getUserSelfSaga,
  removeUserActionSaga,
  updateNetworksForUserSaga,
  updateUserActionSaga,
  updateUserPreferencesSaga,
  updateUserSaga,
} from './user';
import { createBasicAction } from './utils';

export const STARTUP = 'startup';

type StartupAction = FSA<typeof STARTUP>;

const StartupAction = createBasicAction<StartupAction>(STARTUP);

function* startupSaga() {
  yield put(StartupAction());
  yield put(loadGenres());
  yield put(loadNetworks());
}

export function* root() {
  // Wait for any storage state rehydration to complete
  yield take(REHYDRATE);

  // Start watching for auth state changes
  yield fork(watchAuthState);

  // Wait for a user state change (determine whether we're logged in or out)
  yield take('USER_STATE_CHANGE');

  // Instruct the app we're finished booting
  yield put({ type: 'boot/DONE' });

  // Start all of the sagas at once
  // TODO: fork all of these?
  yield all([
    checkAuthSaga(),
    retrieveListSaga(),
    retrieveListsSaga(),
    addToListSaga(),
    searchSaga(),
    loginSaga(),
    logoutSaga(),
    signupSaga(),
    authWithGoogleSaga(),
    getUserSelfSaga(),
    updateListSaga(),
    loadNetworksSaga(),
    updateNetworksForUserSaga(),
    updateUserPreferencesSaga(),
    updateUserSaga(),
    createNewListSaga(),
    deleteListSaga(),
    updateListTrackingSaga(),
    updateUserActionSaga(),
    removeUserActionSaga(),
    upcomingAvailabilitySaga(),
    fetchItemDetailsSaga(),
    allAvailabilitySaga(),
    popularSaga(),
    fetchPersonDetailsSaga(),
    loadGenresSaga(),
    startupSaga(),
    networkAvailabilitySaga(),
  ]);
}
