import { put } from 'redux-saga/effects';

import UserActions from '../Redux/UserRedux';

export function * startup(): IterableIterator<any> {
  yield put(UserActions.userRequest('1')) // TODO: Pull user ID from loaded state
}
