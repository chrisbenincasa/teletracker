import { ApiResponse } from 'apisauce';
import { Navigation } from 'react-native-navigation';
import { all, call, put } from 'redux-saga/effects';

import { User } from '../Model';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function * getUser(api: TeletrackerApi, action: any) {
    const response: ApiResponse<User> = yield call([api, api.getUserSelf]);

    if (response.ok) {
        yield put(UserActions.userSuccess(response.data));
    } else {
        yield put(UserActions.userFailure());
    }
}

export function * signupUser(api: TeletrackerApi, action: any) {
    const { componentId, username, userEmail, password } = action;
    const response: ApiResponse<any> = yield call([api, api.registerUser], username, userEmail, password);

    if (response.ok) {
        yield all([
            put(UserActions.userSignupSuccess(response.data.data)),
            call([Navigation, Navigation.setStackRoot], componentId, {
                component: {
                    name: 'navigation.main.ListView',
                    options: {
                        animated: true,
                        topBar: {
                            visible: false
                        }
                    }
                }
            })
        ]);
    } else {
        yield put(UserActions.userSignupFailure());
    }
}