import { call, put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import ReactGA from 'react-ga';
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
      yield put(logoutSuccessful());

      ReactGA.event({
        category: 'User',
        action: 'Logout',
      });
    } catch (e) {
      console.error(e);
    }
  });
};
