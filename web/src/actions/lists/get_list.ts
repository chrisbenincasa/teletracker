import { put, takeEvery } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { ApiList, List, ListFactory } from '../../types';
import { TeletrackerResponse } from '../../utils/api-client';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';

export const LIST_RETRIEVE_INITIATED = 'lists/retrieve/INITIATED';
export const LIST_RETRIEVE_SUCCESS = 'lists/retrieve/SUCCESS';
export const LIST_RETRIEVE_FAILED = 'lists/retrieve/FAILED';

export interface ListRetrieveInitiatedPayload {
  readonly listId: string;
}

export type ListRetrieveInitiatedAction = FSA<
  typeof LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiatedPayload
>;

export type ListRetrieveSuccessPayload = {
  readonly list: List;
};

export type ListRetrieveSuccessAction = FSA<
  typeof LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessPayload
>;

export type ListRetrieveFailedAction = FSA<typeof LIST_RETRIEVE_FAILED, Error>;

export const getList = createAction<ListRetrieveInitiatedAction>(
  LIST_RETRIEVE_INITIATED,
);

export const ListRetrieveSuccess = createAction<ListRetrieveSuccessAction>(
  LIST_RETRIEVE_SUCCESS,
);

export const ListRetrieveFailed = createAction<ListRetrieveFailedAction>(
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
        let response: TeletrackerResponse<ApiList> = yield clientEffect(
          client => client.getList,
          payload.listId,
        );

        if (response.ok && response.data) {
          yield put(
            ListRetrieveSuccess({
              list: ListFactory.create(response.data.data),
            }),
          );
        } else {
          yield put(ListRetrieveFailed(new Error('bad response')));
        }
      } catch (e) {
        yield put(ListRetrieveFailed(e));
      }
    } else {
      yield put(ListRetrieveFailed(new Error('No payload defined.')));
    }
  });
};
