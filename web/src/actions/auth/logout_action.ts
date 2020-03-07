import { call, put, takeLatest } from '@redux-saga/core/effects';

import { FSA } from 'flux-standard-action';
import { createBasicAction } from '../utils';
import ReactGA from 'react-ga';
import Auth from '@aws-amplify/auth';

export const LOGOUT_INITIATED = 'logout/INITIATED';
export const LOGOUT_SUCCESSFUL = 'logout/SUCCESSFUL';
export const LOGOUT_FAILED = 'logout/FAILED';

export type LogoutInitiatedAction = FSA<typeof LOGOUT_INITIATED>;
export type LogoutSuccessfulAction = FSA<typeof LOGOUT_SUCCESSFUL>;

export const LogoutInitiated = createBasicAction<LogoutInitiatedAction>(
  LOGOUT_INITIATED,
);

export const LogoutSuccessful = createBasicAction<LogoutSuccessfulAction>(
  LOGOUT_SUCCESSFUL,
);

/**
 * Saga that handles the logout flow.
 */
export const logoutSaga = function*() {
  yield takeLatest(LOGOUT_INITIATED, function*() {
    try {
      yield call([Auth, Auth.signOut]);
      yield put(LogoutSuccessful());

      ReactGA.event({
        category: 'User',
        action: 'Logout',
      });
    } catch (e) {
      console.error(e);
    }
  });
};

/**
 * Create a new LogoutInitiated, which when dispatched starts the
 * logoutSaga
 */
export const logout = () => {
  return LogoutInitiated();
};
