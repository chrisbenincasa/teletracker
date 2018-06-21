import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { all, call, put, select } from 'redux-saga/effects';

import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function* addToList(api: TeletrackerApi, { componentId, itemId }: AnyAction) {
    const state = yield select();

    const listId = state.user.details.lists[0].id; // TOOD: Use listId that is passed in

    const response: ApiResponse<any> = yield call([api, api.addItemToList], listId, itemId);

    if (response.ok) {
        yield all([
            put(UserActions.userRequest(componentId)),
            put(ListActions.addToListSuccess(response.data))
        ]);
        yield put(ListActions.addToListSuccess(response.data));
    } else {
        yield put(ListActions.addToListFailure());
    }
}