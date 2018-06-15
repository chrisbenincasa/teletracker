import { ApiResponse } from 'apisauce';
import { Navigation } from 'react-native-navigation';
import { all, call, put } from 'redux-saga/effects';

import { User } from '../Model';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';
import { AnyAction } from 'redux';

export function * getUser(api: TeletrackerApi, action: any) {
    const response: ApiResponse<User> = yield call([api, api.getUserSelf]);

    if (response.ok) {
        yield put(UserActions.userSuccess(response.data));
    } else {
        yield put(UserActions.userFailure());
    }
}

const getListViewNavEffect = (componentId: string) => {
    return call([Navigation, Navigation.setStackRoot], componentId, {
        component: {
            name: 'navigation.main.ListView',
            options: {
                animated: true,
                topBar: {
                    visible: false
                }
            }
        }
    });
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
        yield all([
            put(UserActions.userSignupSuccess(response.data.data)),
            getListViewNavEffect(componentId)
        ]);
    } else {
        yield put(UserActions.userSignupFailure());
    }
}