import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { call, put } from 'redux-saga/effects';

import SearchActions from '../Redux/SearchRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { tracker, appVersion } from '../Components/Analytics';

export function * search(api: TeletrackerApi, { searchText }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.search], searchText);

    if (response.ok) {
        // Track API response duration
        tracker.trackTiming('api', response.duration, {
            name: 'search',
            label: appVersion
        });
        yield put(SearchActions.searchSuccess(response.data));
    } else {
        // Track failed search in GA
        tracker.trackException(response.problem, false);

        yield put(SearchActions.searchFailure());
    }
}