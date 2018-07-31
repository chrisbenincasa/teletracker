import { ApiResponse } from 'apisauce';
import { Navigation } from 'react-native-navigation';
import { AnyAction } from 'redux';
import { all, call, put } from 'redux-saga/effects';

import { appVersion, tracker } from '../Components/Analytics';
import { User } from '../Model';
import * as NavigationConfig from '../Navigation/NavigationConfig';
import NavigationService from '../Navigation/NavigationService';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

const getListViewNavEffect = (componentId: string) => {
    return call([Navigation, Navigation.setStackRoot], componentId, NavigationConfig.ListBottomTabs);
}

const getNavEffect = (componentId: string, view: any) => {
    return call([Navigation, Navigation.setStackRoot], componentId, view);
}

export function* getUser(api: TeletrackerApi, { componentId }: AnyAction) {
    const response: ApiResponse<User> = yield call([api, api.getUserSelf]);

    if (response.ok) {
        // Track API response duration
        tracker.trackTiming('api', response.duration, { 
            name: 'getUser',
            label: appVersion
        });
        yield put(UserActions.userSuccess(response.data));
    } else {
        tracker.trackException(response.problem, false);
        yield all([
            getNavEffect(componentId, NavigationConfig.LoginScreenComponent),
            put(UserActions.userFailure())
        ]);
    }
}

export function * loginUser(api: TeletrackerApi, action: AnyAction) {
    const { email, password } = action;
    const response: ApiResponse<any> = yield call([api, api.loginUser], email, password);

    if (response.ok) {
        // Track successful logins in GA
        tracker.trackTiming('api', response.duration, {
            name: 'loginUser',
            label: appVersion
        });
        tracker.setUser(response.data.data.userId.toString());
        tracker.trackEvent('user', 'login');

        yield call(getUser, api, action);
        yield all([
            put(UserActions.loginSuccess(response.data.data.token)),
            call([NavigationService, NavigationService.navigate], 'App'),
            // call([NavigationService, NavigationService.reset], [NavigationActions.navigate({ routeName: 'App' })])
        ]);
    } else {
        // Track login failures in GA
        tracker.trackException(response.problem, false);

        yield put(UserActions.loginFailure());
    }
}

export function * logoutUser(api: TeletrackerApi) {
    const response: ApiResponse<any> = yield call([api, api.logoutUser]);

    if (response.ok) {
        // Track logout success in GA
        tracker.trackTiming('api', response.duration, {
            name: 'logoutUser',
            label: appVersion
        });
        tracker.trackEvent('user', 'logout');

        yield all([
            put(UserActions.logoutSuccess()),
            call([Navigation, Navigation.setRoot], NavigationConfig.AuthStack2)
        ]);
    } else {
        // Track logout failures in GA
        tracker.trackException(response.problem, false);
        console.tron.log('uh oh');
    }
}

export function * signupUser(api: TeletrackerApi, action: any) {
    const { username, userEmail, password } = action;
    const response: ApiResponse<any> = yield call([api, api.registerUser], username, userEmail, password);

    if (response.ok) {
        // Track successful signups in GA
        tracker.trackTiming('api', response.duration, {
            name: 'signupUser',
            label: appVersion
        });

        tracker.setUser(response.data.data.userId.toString());
        tracker.trackEvent('user', 'signup');

        yield put(UserActions.userSignupSuccess(response.data.data.token));
        // Kick off a getUser call
        yield call(getUser, api, action);
        yield call([NavigationService, NavigationService.navigate], 'ListOfLists');
        // yield getListViewNavEffect(componentId)
    } else {
        // Track signup failures in GA
        tracker.trackException(response.problem, false);

        yield put(UserActions.userSignupFailure());
    }
}

export function * postEvent(api: TeletrackerApi, {componentId, eventType, targetType, targetId}: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.postEvent], eventType, targetType, targetId, '');
    console.log(response);
}