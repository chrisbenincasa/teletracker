import { all, AllEffect, takeLatest } from 'redux-saga/effects';

import { EventTypes } from '../Redux/EventsRedux';
import { ItemTypes } from '../Redux/ItemRedux';
import { ListTypes } from '../Redux/ListRedux';
import { SearchTypes } from '../Redux/SearchRedux';
import { StartupTypes } from '../Redux/StartupRedux';
import { UserTypes } from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { retrieveEvents } from './EventsSagas';
import { fetchShow } from './ItemSagas';
import { addToList, createList, updateListTracking } from './ListSagas';
import { search } from './SearchSagas';
import { startup } from './StartupSagas';
import { getUser, loginUser, logoutUser, postEvent, signupUser } from './UserSagas';

export const teletrackerApi = new TeletrackerApi();

export default function * root(): IterableIterator<AllEffect> {
  yield all([
    takeLatest(StartupTypes.STARTUP, startup, teletrackerApi),

    takeLatest(UserTypes.USER_REQUEST, getUser, teletrackerApi),
    takeLatest(UserTypes.LOGIN_REQUEST, loginUser, teletrackerApi),
    takeLatest(UserTypes.LOGOUT_REQUEST, logoutUser, teletrackerApi),
    takeLatest(UserTypes.USER_SELF_REQUEST, getUser, teletrackerApi),
    takeLatest(UserTypes.USER_SIGNUP_REQUEST, signupUser, teletrackerApi),
    takeLatest(UserTypes.POST_EVENT, postEvent, teletrackerApi),

    takeLatest(SearchTypes.SEARCH_REQUEST, search, teletrackerApi),

    takeLatest(ListTypes.ADD_TO_LIST, addToList, teletrackerApi),
    takeLatest(ListTypes.CREATE_LIST, createList, teletrackerApi),
    takeLatest(ListTypes.UPDATE_LIST_TRACKING, updateListTracking, teletrackerApi),
    
    takeLatest(EventTypes.RETRIEVE_EVENTS, retrieveEvents, teletrackerApi),

    takeLatest(ItemTypes.FETCH_SHOW, fetchShow, teletrackerApi)
  ]);
}
