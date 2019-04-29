import { put, select, takeEvery, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_ALL_INITIATED,
  LIST_RETRIEVE_ALL_SUCCESS,
  LIST_RETRIEVE_FAILED,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
  LIST_UPDATE_INITIATED,
} from '../constants/lists';
import { AppState } from '../reducers';
import { List, User } from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { TeletrackerResponse } from '../utils/api-client';
import { clientEffect, createAction } from './utils';
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

export interface ListRetrieveInitiatedPayload {
  listId: string | number;
  force?: boolean;
}

export type ListRetrieveInitiatedAction = FSA<
  typeof LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiatedPayload
>;

export type ListRetrieveSuccessAction = FSA<typeof LIST_RETRIEVE_SUCCESS, List>;

export type ListRetrieveFailedAction = FSA<typeof LIST_RETRIEVE_FAILED, Error>;

export interface ListRetrieveAllPayload {
  metadataFields?: KeyMap<ObjectMetadata>;
}

export type ListRetrieveAllInitiatedAction = FSA<
  typeof LIST_RETRIEVE_ALL_INITIATED,
  ListRetrieveAllPayload
>;

export type ListRetrieveAllSuccessAction = FSA<
  typeof LIST_RETRIEVE_ALL_SUCCESS,
  User
>;

export interface ListUpdatedInitiatedPayload {
  thingId: number;
  addToLists: string[];
  removeFromLists: string[];
}

export type ListUpdateInitiatedAction = FSA<
  typeof LIST_UPDATE_INITIATED,
  ListUpdatedInitiatedPayload
>;

const ListAddInitiated = createAction<ListAddInitiatedAction>(
  LIST_ADD_ITEM_INITIATED,
);

export const ListRetrieveInitiated = createAction<ListRetrieveInitiatedAction>(
  LIST_RETRIEVE_INITIATED,
);

export const ListRetrieveAllInitiated = createAction<
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

export const ListUpdate = createAction<ListUpdateInitiatedAction>(
  LIST_UPDATE_INITIATED,
);

type ListAddActions =
  | ListAddInitiatedAction
  | ListAddSuccessAction
  | ListAddFailedAction
  | ListRetrieveInitiatedAction
  | ListRetrieveSuccessAction
  | ListRetrieveFailedAction;

export type ListActions = ListAddActions;

/**
 * Listens for `LIST_ADD_ITEM_INITIATED` actions and then
 * attempts to add the specified item to the given list
 */
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
      // TODO: Make error action typed
      yield put({
        type: LIST_ADD_ITEM_FAILED,
        error: true,
        payload: new Error('No payload passed on LIST_ADD_ITEM_INITIATED'),
      });
    }
  });
};

/**
 * Alias for creating a new ListAddInitiated action
 */
export const addToList = (listId: string, itemId: string) => {
  return ListAddInitiated({ listId, itemId });
};

/**
 * Listens for `LIST_RETRIEVE_INITIATED` actions and then attempts to fetch the specified list from
 * the payload
 */
export const retrieveListSaga = function*() {
  yield takeEvery(LIST_RETRIEVE_INITIATED, function*({
    payload,
  }: ListRetrieveInitiatedAction) {
    if (payload) {
      try {
        let currState: AppState = yield select();

        if (currState.lists.listsById[payload.listId] && !payload.force) {
          yield put(
            ListRetrieveSuccess(currState.lists.listsById[payload.listId]),
          );
        } else {
          // TODO: Type alias to make this cleaner
          let response: TeletrackerResponse<List> = yield clientEffect(
            client => client.getList,
            payload.listId,
          );

          if (response.ok && response.data) {
            yield put(ListRetrieveSuccess(response.data.data));
          } else {
            yield put(ListRetrieveFailed(new Error('bad response')));
          }
        }
      } catch (e) {
        yield put(ListRetrieveFailed(e));
      }
    } else {
      yield put(ListRetrieveFailed(new Error('No payload defined.')));
    }
  });
};

const defaultMovieMeta: KeyMap<ObjectMetadata> = {
  themoviedb: {
    movie: {
      title: true,
      id: true,
      poster_path: true,
      overview: true,
    },
  },
};

/**
 * Listens for `LIST_RETRIEVE_ALL_INITIATED` actions and retrieves a clients full list of lists.
 * Optionally, dispatchers of this action can provide a map indicating which metadata fields to return
 * in the response. The reducer deep merges the returned list metadata against existing state
 */
export const retrieveListsSaga = function*() {
  yield takeEvery(LIST_RETRIEVE_ALL_INITIATED, function*({
    payload,
  }: ListRetrieveAllInitiatedAction) {
    let metadataToFetch =
      payload && payload.metadataFields
        ? payload.metadataFields
        : defaultMovieMeta;

    let response = yield clientEffect(
      client => client.getLists,
      metadataToFetch,
    );

    if (response.ok && response.data) {
      // yield put(RetrieveUserSelfSuccess(response.data!.data));
      console.log(response.data.data);
      yield put(ListRetrieveAllSuccess(response.data.data));
    } else {
      console.error('bad');
    }
  });
};

export const updateListSaga = function*() {
  yield takeLatest(LIST_UPDATE_INITIATED, function*({
    payload,
  }: ListUpdateInitiatedAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateListTracking,
        payload.thingId,
        payload.addToLists,
        payload.removeFromLists,
      );

      if (response.ok) {
        yield put(ListRetrieveAllInitiated({}));
      }
    } else {
    }
  });
};
