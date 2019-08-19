import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction } from '../utils';
import { KeyMap, ObjectMetadata } from '../../types/external/themoviedb/Movie';
import { FSA } from 'flux-standard-action';
import { List } from '../../types';

export const defaultMovieMeta = {
  themoviedb: {
    movie: {
      title: true,
      id: true,
      poster_path: true,
      overview: true,
    },
    show: true,
  },
} as KeyMap<ObjectMetadata>;

export const LIST_RETRIEVE_ALL_INITIATED = 'lists/retrieve_all/INITIATED';
export const LIST_RETRIEVE_ALL_SUCCESS = 'lists/retrieve_all/SUCCESS';
export const LIST_RETRIEVE_ALL_FAILED = 'lists/retrieve_all/FAILED';

export interface ListRetrieveAllPayload {
  metadataFields?: KeyMap<ObjectMetadata>;
  includeThings?: boolean;
}

export type ListRetrieveAllInitiatedAction = FSA<
  typeof LIST_RETRIEVE_ALL_INITIATED,
  ListRetrieveAllPayload
>;

export type ListRetrieveAllSuccessAction = FSA<
  typeof LIST_RETRIEVE_ALL_SUCCESS,
  List[]
>;

const ListRetrieveAllSuccess = createAction<ListRetrieveAllSuccessAction>(
  LIST_RETRIEVE_ALL_SUCCESS,
);

export const retrieveAllLists = createAction<ListRetrieveAllInitiatedAction>(
  LIST_RETRIEVE_ALL_INITIATED,
);

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
      payload ? payload.includeThings : true,
    );

    if (response.ok && response.data) {
      // yield put(RetrieveUserSelfSuccess(response.data!.data));
      yield put(ListRetrieveAllSuccess(response.data.data));
    } else {
      console.error('bad');
    }
  });
};
