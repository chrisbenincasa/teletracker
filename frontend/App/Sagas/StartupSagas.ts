import { Navigation } from 'react-native-navigation';
import { call } from 'redux-saga/effects';

export function * startup(): IterableIterator<any> {
  yield call([Navigation, Navigation.setRoot], {
    root: {
      component: {
        name: 'navigation.main.ListView',
        options: {
          animated: true,
          topBar: {
            visible: false
          }
        }
      }
    }
  });
}
