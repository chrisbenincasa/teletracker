import { ApiResponse } from 'apisauce';
import { Navigation } from 'react-native-navigation';
import { call, select, take, put } from 'redux-saga/effects';

import { appLaunched } from '../Navigation/AppNavigation';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import UserActions from '../Redux/UserRedux';

export function* startup(teletrackerApi: TeletrackerApi): IterableIterator<any> {
  const state = yield select();

  console.tron.log(state);

  const hasSavedToken = !!state.user.token;

  if (hasSavedToken) {
    teletrackerApi.setToken(state.user.token);
  }

  const authStatus: ApiResponse<any> = yield teletrackerApi.getAuthStatus();

  const isLoggedIn = authStatus.status != 401 && authStatus.data.authenticted;

  if (!isLoggedIn) {
    teletrackerApi.clearToken();
    yield put(UserActions.userLogout());
  }

  const view = isLoggedIn ? NavigationConfig.AppStack : NavigationConfig.AuthStack;

  if (!appLaunched()) {
    // Waits until Navigation fires "registerAppLaunchedListener"
    yield take('navigation/registerAppLaunchedListener');
  }

  yield call([Navigation, Navigation.setRoot], view);
}
