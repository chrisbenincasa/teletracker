import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { all, call, put } from 'redux-saga/effects';

import { appVersion, tracker } from '../Components/Analytics';
import NavigationService from '../Navigation/NavigationService';
import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function* addToList(api: TeletrackerApi, { componentId, listId, itemId }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.addItemToList], listId, itemId);

    if (response.ok) {
        // Track API response duration
        tracker.trackTiming('api', response.duration, {
            name: 'addToList',
            label: appVersion
        });
        yield all([
            put(UserActions.userRequest(componentId)),
            put(ListActions.addToListSuccess(response.data))
        ]);
    } else {
        // Track add to list failures in GA
        tracker.trackException(response.problem, false);

        yield put(ListActions.addToListFailure());
    }
}

export function* createList(api: TeletrackerApi, { name }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.createList], name);

    if (response.ok) {
        yield all([
            put(ListActions.createListSuccess()),
            put(UserActions.appendList({ id: response.data.data.id, name, things: [] }))
        ])
    } else {
        console.tron.log('uh oh')
    }
}

export function* updateListTracking(api: TeletrackerApi, { thing, adds, removes }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.updateListTracking], thing.id, adds, removes);

    if (response.ok) {
        yield all([
            put(ListActions.updateListTrackingComplete()),
            put(UserActions.updateLists(thing, adds, removes)),
            call([NavigationService, NavigationService.pop])
        ])
    } else {
        console.tron.log('uh oh')
    }
}