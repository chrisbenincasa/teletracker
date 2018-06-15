import { ApiResponse } from 'apisauce';
import { call, put, all } from 'redux-saga/effects';

import { User } from '../Model';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { NavigationActions } from 'react-navigation';
import NavigationService from '../Services/NavigationService';

export function * getUser(api: TeletrackerApi, action: any) {
    const response: ApiResponse<User> = yield call([api, api.getUserSelf]);

    if (response.ok) {
        yield put(UserActions.userSuccess(response.data));
    } else {
        yield put(UserActions.userFailure());
    }
}

export function * signupUser(api: TeletrackerApi, action: any) {
    const { username, userEmail, password } = action;
    const response: ApiResponse<any> = yield call([api, api.registerUser], username, userEmail, password);

    if (response.ok) {
        yield all([
            put(UserActions.userSignupSuccess(response.data.data)),
            call([NavigationService, NavigationService.navigate], 'ItemList')
        ]);
    } else {
        yield put(UserActions.userSignupFailure());
    }
}