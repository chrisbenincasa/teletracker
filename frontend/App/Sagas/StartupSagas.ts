import { Navigation } from 'react-native-navigation';
import { call, select, take } from 'redux-saga/effects';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import { appLaunched } from '../Navigation/AppNavigation';

export function * startup(): IterableIterator<any> {
  const state = yield select();

  const isLoggedIn = !!state.user.token;

  const view = isLoggedIn ? NavigationConfig.AppStack : NavigationConfig.AuthStack;

  if (!appLaunched()) {
    // Waits until Navigation fires "registerAppLaunchedListener"
    yield take('navigation/registerAppLaunchedListener');
  }

  yield call([Navigation, Navigation.setRoot], view);
}
