import { ApiResponse } from 'apisauce';
import { call, put } from 'redux-saga/effects';

import { User } from '../Model';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function * getUser(api: TeletrackerApi, action: any) {
    const { userId } = action;
    const response: ApiResponse<User> = yield call([api, api.getUser], userId);

    if (response.ok) {
        yield put(UserActions.userSuccess(response.data));
    } else {
        yield put(UserActions.userFailure());
    }
}