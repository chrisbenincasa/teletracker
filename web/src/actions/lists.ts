import { put, takeEvery, takeLatest } from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
import { FSA } from 'flux-standard-action';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_ALL_INITIATED,
  LIST_RETRIEVE_FAILED,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
  LIST_RETRIEVE_ALL_SUCCESS,
} from '../constants/lists';
import { List, User } from '../types';
import { DataResponse } from '../utils/api-client';
import { clientEffect, createAction, createBasicAction } from './utils';
import { RetrieveUserSelfSuccess } from './user';

interface ListAddInitiatedPayload {
  listId: string;
  itemId: string;
}

export type ListAddInitiatedAction = FSA<
  typeof LIST_ADD_ITEM_INITIATED,
  ListAddInitiatedPayload
>;

export type ListAddSuccessAction = FSA<typeof LIST_ADD_ITEM_SUCCESS>;

export type ListAddFailedAction = FSA<typeof LIST_ADD_ITEM_FAILED>;

export type ListRetrieveInitiatedAction = FSA<
  typeof LIST_RETRIEVE_INITIATED,
  string | number
>;

export type ListRetrieveSuccessAction = FSA<typeof LIST_RETRIEVE_SUCCESS, List>;

export type ListRetrieveFailedAction = FSA<typeof LIST_RETRIEVE_FAILED, Error>;

export type ListRetrieveAllInitiatedAction = FSA<
  typeof LIST_RETRIEVE_ALL_INITIATED
>;

export type ListRetrieveAllSuccessAction = FSA<
  typeof LIST_RETRIEVE_ALL_SUCCESS,
  User
>;

const ListAddInitiated = createAction<ListAddInitiatedAction>(
  LIST_ADD_ITEM_INITIATED,
);

const ListRetrieveInitiated = createAction<ListRetrieveInitiatedAction>(
  LIST_RETRIEVE_INITIATED,
);

export const ListRetrieveAllInitiated = createBasicAction<
  ListRetrieveAllInitiatedAction
>(LIST_RETRIEVE_ALL_INITIATED);

const ListRetrieveSuccess = createAction<ListRetrieveSuccessAction>(
  LIST_RETRIEVE_SUCCESS,
);

const ListRetrieveAllSuccess = createAction<ListRetrieveAllSuccessAction>(
  LIST_RETRIEVE_ALL_SUCCESS,
);

const ListRetrieveFailed = createAction<ListRetrieveFailedAction>(
  LIST_RETRIEVE_FAILED,
);

type ListAddActions =
  | ListAddInitiatedAction
  | ListAddSuccessAction
  | ListAddFailedAction
  | ListRetrieveInitiatedAction
  | ListRetrieveSuccessAction
  | ListRetrieveFailedAction;

export type ListActions = ListAddActions;

export const addToListSaga = function*() {
  yield takeEvery(LIST_ADD_ITEM_INITIATED, function*({
    payload,
  }: ListAddInitiatedAction) {
    if (payload) {
      try {
        let response = yield clientEffect(
          client => client.addItemToList,
          payload.listId,
          payload.itemId,
        );
        if (response.ok) {
          yield put({ type: LIST_ADD_ITEM_SUCCESS });
          // TODO: put a retrieve user action here
        } else {
          yield put({ type: LIST_ADD_ITEM_FAILED });
        }
      } catch (e) {
        yield put({ type: LIST_ADD_ITEM_FAILED });
      }
    } else {
      // TODO: Error
    }
  });
};

export const addToList = (listId: string, itemId: string) => {
  return ListAddInitiated({ listId, itemId });
};

export const retrieveListSaga = function*() {
  yield takeEvery(LIST_RETRIEVE_INITIATED, function*({
    payload,
  }: ListRetrieveInitiatedAction) {
    if (payload) {
      try {
        // TODO: Type alias to make this cleaner
        let response: ApiResponse<DataResponse<List>> = yield clientEffect(
          client => client.getList,
          payload,
        );

        if (response.ok && response.data) {
          yield put(ListRetrieveSuccess(response.data.data));
        } else {
          yield put(ListRetrieveFailed(new Error('bad response')));
        }
      } catch (e) {
        yield put(ListRetrieveFailed(e));
      }
    } else {
      // TODO: ERROR
    }
  });
};

export const retrieveListsSaga = function*() {
  yield takeLatest(LIST_RETRIEVE_ALL_INITIATED, function*() {
    let response = yield clientEffect(client => client.getLists, {
      themoviedb: {
        movie: {
          title: true,
          id: true,
          poster_path: true,
        },
      },
    });

    if (response.ok && response.data) {
      yield put(RetrieveUserSelfSuccess(response.data!.data));
      yield put(ListRetrieveAllSuccess(response.data.data));
    }
  });
};

export const retrieveList = (listId: string, force: boolean = false) => {
  return ListRetrieveInitiated(listId);
};
