import { ApiResponse } from 'apisauce';
import { AnyAction } from 'redux';
import { call, put } from 'redux-saga/effects';

import SearchActions from '../Redux/SearchRedux';
import { TeletrackerApi } from '../Services/TeletrackerApi';

export function * search(api: TeletrackerApi, { searchText }: AnyAction) {
    const response: ApiResponse<any> = yield call([api, api.search], searchText);

    if (response.ok) {
        yield put(SearchActions.searchSuccess(response.data));
    } else {
        yield put(SearchActions.searchFailure());
    }
}