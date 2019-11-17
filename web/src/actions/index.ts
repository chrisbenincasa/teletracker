import { all, fork, put, take } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { REHYDRATE } from 'redux-persist';
import {
  authStateWatcher,
  authWithGoogleSaga,
  initialAuthState,
  loginSaga,
  logoutSaga,
  signupSaga,
  USER_STATE_CHANGE,
} from './auth';
import { allAvailabilitySaga, upcomingAvailabilitySaga } from './availability';
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
import { loadNetworksSaga } from './metadata';
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
import { exploreSaga } from './explore';
import { filtersChangedSaga } from './filters';
import { loadMetadata, loadMetadataSaga } from './metadata/load_metadata';
import { peopleSearchSaga } from './search/person_search';
import { fetchPeopleDetailsSaga } from './people/get_people';

export const STARTUP = 'startup';
export const BOOT_DONE = 'boot/DONE';

type StartupAction = FSA<typeof STARTUP>;

const StartupAction = createBasicAction<StartupAction>(STARTUP);

function* startupSaga() {
  yield put(StartupAction());
  yield put(loadMetadata());
}

export function* root() {
  // Wait for any storage state rehydration to complete
  yield take(REHYDRATE);

  // Start watching for auth state changes
  yield fork(initialAuthState);
  yield fork(authStateWatcher);

  // Wait for a user state change (determine whether we're logged in or out)
  yield take(USER_STATE_CHANGE);

  // Instruct the app we're finished booting
  yield put({ type: BOOT_DONE });

  // Start all of the sagas at once
  // TODO: fork all of these?
  yield all([
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
    fetchPeopleDetailsSaga(),
    loadGenresSaga(),
    startupSaga(),
    exploreSaga(),
    filtersChangedSaga(),
    loadMetadataSaga(),
    peopleSearchSaga(),
  ]);
}
