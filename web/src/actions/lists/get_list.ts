import { put, select, takeEvery } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { AppState } from '../../reducers';
import { List } from '../../types';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect } from '../utils';
import { createAction } from '../utils';

export const LIST_RETRIEVE_INITIATED = 'lists/retrieve/INITIATED';
export const LIST_RETRIEVE_SUCCESS = 'lists/retrieve/SUCCESS';
export const LIST_RETRIEVE_FAILED = 'lists/retrieve/FAILED';

export interface ListRetrieveInitiatedPayload {
  listId: number;
  force?: boolean;
  sort?: 'popular' | 'recent' | 'added_time' | 'default';
  desc?: boolean;
}

export type ListRetrieveInitiatedAction = FSA<
  typeof LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiatedPayload
>;

export type ListRetrieveSuccessAction = FSA<typeof LIST_RETRIEVE_SUCCESS, List>;

export type ListRetrieveFailedAction = FSA<typeof LIST_RETRIEVE_FAILED, Error>;

export const ListRetrieveInitiated = createAction<ListRetrieveInitiatedAction>(
  LIST_RETRIEVE_INITIATED,
);

const ListRetrieveSuccess = createAction<ListRetrieveSuccessAction>(
  LIST_RETRIEVE_SUCCESS,
);

const ListRetrieveFailed = createAction<ListRetrieveFailedAction>(
  LIST_RETRIEVE_FAILED,
);

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
            payload.sort,
            payload.desc,
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
