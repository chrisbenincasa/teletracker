import { Navigation } from 'react-native-navigation';
import { AnyAction } from 'redux';
import { call } from 'redux-saga/effects';

export function * pushState({ componentId, view }: AnyAction) {
  console.tron.log('pushing view: ', view);
  yield call([Navigation, Navigation.push], componentId, view);
}

// export function * goToItemDetail({ componentId, needsFetch, itemId }: AnyAction) {
//   // if (needsFetch) {
//   //   let x = yield* pushState()
//   // }


// }