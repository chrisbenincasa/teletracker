import { ApiResponse } from 'apisauce';
import { call, put } from 'redux-saga/effects';

import EventsActions from '../Redux/EventsRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function * retrieveEvents(api: TeletrackerApi) {
    let response: ApiResponse<any> = yield call([api, api.getEvents]);

    if (response.ok) {
        yield put(EventsActions.retrieveEventsSuccess(response.data));
    } else {
        yield put(EventsActions.retrieveEventsFailure());

    }
}