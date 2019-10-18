import { put, select, takeEvery } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { AppState } from '../../reducers';
import {
  ItemTypes,
  List,
  ListSortOptions,
  NetworkTypes,
  Paging,
} from '../../types';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect } from '../utils';
import { createAction } from '../utils';
import _ from 'lodash';

export const LIST_RETRIEVE_INITIATED = 'lists/retrieve/INITIATED';
export const LIST_RETRIEVE_SUCCESS = 'lists/retrieve/SUCCESS';
export const LIST_RETRIEVE_FAILED = 'lists/retrieve/FAILED';

export interface ListRetrieveInitiatedPayload {
  listId: number;
  force?: boolean;
  sort?: ListSortOptions;
  desc?: boolean;
  itemTypes?: ItemTypes[];
  genres?: number[];
  bookmark?: string;
  networks?: NetworkTypes[];
}

export type ListRetrieveInitiatedAction = FSA<
  typeof LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiatedPayload
>;

export type ListRetrieveSuccessPayload = {
  list: List;
  paging?: Paging;
  append: boolean;
};

export type ListRetrieveSuccessAction = FSA<
  typeof LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessPayload
>;

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
        // TODO: Type alias to make this cleaner
        let response: TeletrackerResponse<List> = yield clientEffect(
          client => client.getList,
          payload.listId,
          payload.sort,
          payload.desc,
          payload.itemTypes,
          payload.genres,
          payload.bookmark,
        );

        if (response.ok && response.data) {
          yield put(
            ListRetrieveSuccess({
              list: response.data.data,
              paging: response.data.paging,
              append: !_.isUndefined(payload.bookmark),
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
