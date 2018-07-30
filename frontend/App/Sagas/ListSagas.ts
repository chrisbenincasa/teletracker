import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { all, call, put } from 'redux-saga/effects';

import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { tracker } from '../Components/Analytics';

export function* addToList(api: TeletrackerApi, { componentId, listId, itemId }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.addItemToList], listId, itemId);

    if (response.ok) {
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