import { all, call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { logEvent, logException } from '../../utils/analytics';
import Auth from '@aws-amplify/auth';
import { createAction } from '@reduxjs/toolkit';

export const LOGOUT_INITIATED = 'logout/INITIATED';
export const LOGOUT_SUCCESSFUL = 'logout/SUCCESSFUL';
export const LOGOUT_FAILED = 'logout/FAILED';

export type LogoutSuccessfulAction = FSA<typeof LOGOUT_SUCCESSFUL>;

export const logout = createAction(LOGOUT_INITIATED);
export const logoutSuccessful = createAction(LOGOUT_SUCCESSFUL);

/**
 * Saga that handles the logout flow.
 */
export const logoutSaga = function*() {
  yield takeLatest(LOGOUT_INITIATED, function*() {
    try {
      yield call([Auth, Auth.signOut]);
      yield all([
        put(logoutSuccessful()),
        call(logEvent, 'Logout', 'Logout', 'Manual'),
      ]);
    } catch (e) {
      console.error(e);
      yield call(logException, `${e}`, false);
    }
  });
};
