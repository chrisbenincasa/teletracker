import { all, AllEffect, takeLatest } from 'redux-saga/effects';

import { StartupTypes } from '../Redux/StartupRedux';
import { UserTypes } from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { startup } from './StartupSagas';
import { getUser } from './UserSagas';

const token = "<test token here>"
const teletrackerApi = new TeletrackerApi({ token });

export default function * root(): IterableIterator<AllEffect> {
  yield all([
    takeLatest(StartupTypes.STARTUP, startup),
    takeLatest(UserTypes.USER_REQUEST, getUser, teletrackerApi)
  ])
}
