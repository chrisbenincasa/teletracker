import { all, AllEffect, takeLatest } from 'redux-saga/effects';

import { StartupTypes } from '../Redux/StartupRedux';
import { UserTypes } from '../Redux/UserRedux';
import { EventTypes } from '../Redux/EventsRedux';
import { SearchTypes } from '../Redux/SearchRedux';
import { ListTypes } from '../Redux/ListRedux';
import { NavTypes } from '../Redux/NavRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { startup } from './StartupSagas';
import { getUser, signupUser, loginUser, postEvent, logoutUser } from './UserSagas';
import { search } from './SearchSagas';
import { addToList } from './ListSagas';
import { pushState } from './NavSagas';
import { retrieveEvents } from './EventsSagas';

export const teletrackerApi = new TeletrackerApi();

export default function * root(): IterableIterator<AllEffect> {
  yield all([
    takeLatest(StartupTypes.STARTUP, startup, teletrackerApi),
    takeLatest(UserTypes.USER_REQUEST, getUser, teletrackerApi),
    takeLatest(UserTypes.LOGIN_REQUEST, loginUser, teletrackerApi),
    takeLatest(UserTypes.LOGOUT_REQUEST, logoutUser, teletrackerApi),
    takeLatest(UserTypes.USER_SELF_REQUEST, getUser, teletrackerApi),
    takeLatest(UserTypes.USER_SIGNUP_REQUEST, signupUser, teletrackerApi),
    takeLatest(SearchTypes.SEARCH_REQUEST, search, teletrackerApi),
    takeLatest(ListTypes.ADD_TO_LIST, addToList, teletrackerApi),
    takeLatest(NavTypes.PUSH_STATE, pushState),
    takeLatest(UserTypes.POST_EVENT, postEvent, teletrackerApi),
    takeLatest(EventTypes.RETRIEVE_EVENTS, retrieveEvents, teletrackerApi)
  ]);
}
