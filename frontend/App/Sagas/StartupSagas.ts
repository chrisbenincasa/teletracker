import { Navigation } from 'react-native-navigation';
import { call, select, take } from 'redux-saga/effects';

import { appLaunched } from '../Navigation/AppNavigation';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import { teletrackerApi } from '../Sagas';

export function * startup(): IterableIterator<any> {
  const state = yield select();

  console.tron.log(state);
  const isLoggedIn = !!state.user.token;

  if (isLoggedIn) {
    teletrackerApi.setToken(state.user.token);
  }

  const view = isLoggedIn ? NavigationConfig.AppStack : NavigationConfig.AuthStack;

  if (!appLaunched()) {
    // Waits until Navigation fires "registerAppLaunchedListener"
    yield take('navigation/registerAppLaunchedListener');
  }

  yield call([Navigation, Navigation.setRoot], view);
}
