import { Navigation } from 'react-native-navigation';
import { call, select } from 'redux-saga/effects';
import * as NavigationConfig from '../Navigation/NavigationConfig';

export function * startup(): IterableIterator<any> {
  const state = yield select();

  const isLoggedIn = !!state.user.token;

  const view = isLoggedIn ? NavigationConfig.AppStack : NavigationConfig.AuthStack;

  yield call([Navigation, Navigation.setRoot], view);
}
