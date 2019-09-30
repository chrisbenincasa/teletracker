import { put, takeEvery } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { clientEffect, createAction } from '../utils';
import ReactGA from 'react-ga';

export const LIST_ADD_ITEM_INITIATED = 'lists/add_item/INITIATED';
export const LIST_ADD_ITEM_SUCCESS = 'lists/add_item/SUCCESS';
export const LIST_ADD_ITEM_FAILED = 'lists/add_item/FAILED';

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

const ListAddInitiated = createAction<ListAddInitiatedAction>(
  LIST_ADD_ITEM_INITIATED,
);

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

          ReactGA.event({
            category: 'User',
            action: 'Added item to list',
          });
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
