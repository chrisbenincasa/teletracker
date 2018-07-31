import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { all, call, put } from 'redux-saga/effects';

import ListActions from '../Redux/ListRedux';
import UserActions from '../Redux/UserRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function* fetchShow(api: TeletrackerApi, { id }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.getShow], id);

    if (response.ok) {
        console.tron.log(response.data);
    } else {
        
    }
}