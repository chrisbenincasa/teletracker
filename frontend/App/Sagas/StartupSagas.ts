import { ApiResponse } from 'apisauce';
import { call, put, select } from 'redux-saga/effects';

import NavigationService from '../Navigation/NavigationService';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function* startup(teletrackerApi: TeletrackerApi): IterableIterator<any> {
  const state = yield select();

  const hasSavedToken = !!state.user.token;

  if (hasSavedToken) {
    teletrackerApi.setToken(state.user.token);
  }

  const authStatus: ApiResponse<any> = yield teletrackerApi.getAuthStatus();

  const isLoggedIn = authStatus.status != 401 && authStatus.data.data.authenticated;

  if (!isLoggedIn) {
    teletrackerApi.clearToken();
    yield put(UserActions.userLogout());
  }

  const view = isLoggedIn ? 'App' : 'Auth';

  yield call([NavigationService, NavigationService.navigate], view);
}
