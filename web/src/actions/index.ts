import { all, put, take } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { REHYDRATE } from 'redux-persist';
import {
  checkAuthSaga,
  loginSaga,
  logoutSaga,
  signupSaga,
  authWithGoogleSaga,
  watchAuthState,
} from './auth';
import {
  addToListSaga,
  createNewListSaga,
  deleteListSaga,
  updateListSaga,
  retrieveListSaga,
  retrieveListsSaga,
  updateListTrackingSaga,
} from './lists';
import { loadNetworksSaga } from './metadata';
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
import { allAvailabilitySaga, upcomingAvailabilitySaga } from './availability';
import { popularSaga, genreSaga } from './popular';
import { fetchItemDetailsBatchSaga, fetchItemDetailsSaga } from './item-detail';
import { fetchPersonDetailsSaga } from './people/get_person';
import {
  GENRES_LOAD_SUCCESS,
  loadGenres,
  loadGenresSaga,
} from './metadata/load_genres';

export const STARTUP = 'startup';

type StartupAction = FSA<typeof STARTUP>;

const StartupAction = createBasicAction<StartupAction>(STARTUP);

function* startupSaga() {
  yield put(StartupAction());
  yield put(loadGenres());
}

export function* root() {
  // Wait until persisted state is rehydrated
  yield take(REHYDRATE);

  // Start all of the sagas
  yield all([
    watchAuthState(),
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
    fetchItemDetailsBatchSaga(),
    allAvailabilitySaga(),
    popularSaga(),
    fetchPersonDetailsSaga(),
    loadGenresSaga(),
    genreSaga(),
    startupSaga(),
  ]);
}
