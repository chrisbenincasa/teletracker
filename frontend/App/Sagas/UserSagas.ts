import { ApiResponse } from 'apisauce';
import { Navigation } from 'react-native-navigation';
import { all, call, put } from 'redux-saga/effects';

import { User } from '../Model';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { AnyAction } from 'redux';
import * as NavigationConfig from '../Navigation/NavigationConfig';

const getListViewNavEffect = (componentId: string) => {
    return call([Navigation, Navigation.setStackRoot], componentId, NavigationConfig.ListView);
}

const getNavEffect = (componentId: string, view: any) => {
    return call([Navigation, Navigation.setStackRoot], componentId, view);
}

export function* getUser(api: TeletrackerApi, { componentId }: AnyAction) {
    const response: ApiResponse<User> = yield call([api, api.getUserSelf]);

    if (response.ok) {
        yield put(UserActions.userSuccess(response.data));
    } else {
        yield all([
            getNavEffect(componentId, NavigationConfig.LoginScreenComponent),
            put(UserActions.userFailure())
        ]);
    }
}

export function * loginUser(api: TeletrackerApi, { componentId, email, password }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.loginUser], email, password);

    if (response.ok) {
        yield all([
            put(UserActions.loginSuccess(response.data.data.token)),
            getListViewNavEffect(componentId)
        ]);
    } else {
        yield put(UserActions.loginFailure());
    }
}

export function * signupUser(api: TeletrackerApi, action: any) {
    const { componentId, username, userEmail, password } = action;
    const response: ApiResponse<any> = yield call([api, api.registerUser], username, userEmail, password);

    if (response.ok) {
        yield put(UserActions.userSignupSuccess(response.data.data.token));
        // Kick off a getUser call
        yield call(getUser, api, action);
        yield getListViewNavEffect(componentId)
    } else {
        yield put(UserActions.userSignupFailure());
    }
}