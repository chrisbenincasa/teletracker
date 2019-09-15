import { ApiResponse } from 'apisauce';
import { call, put } from 'redux-saga/effects';

import EventsActions from '../Redux/EventsRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { tracker, appVersion } from '../Components/Analytics';

export function * retrieveEvents(api: TeletrackerApi) {
    let response: ApiResponse<any> = yield call([api, api.getEvents]);

    if (response.ok) {
        // Track API response duration
        tracker.trackTiming('api', response.duration, {
            name: 'retrieveEvents',
            label: appVersion
        });
        yield put(EventsActions.retrieveEventsSuccess(response.data));
    } else {
        // Track failed search in GA
        tracker.trackException(response.problem, false);

        yield put(EventsActions.retrieveEventsFailure());
    }
}