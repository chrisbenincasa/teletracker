import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction } from '../utils';
import { defaultMovieMeta } from '../lists';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory } from '../../types/Thing';
import { KeyMap, ObjectMetadata } from '../../types/external/themoviedb/Movie';

export const POPULAR_INITIATED = 'popular/INITIATED';
export const POPULAR_SUCCESSFUL = 'popular/SUCCESSFUL';
export const POPULAR_FAILED = 'popular/FAILED';

export interface PopularInitiatedActionPayload {
  token?: string;
  fields?: KeyMap<ObjectMetadata>;
  itemTypes?: string[];
  network?: string;
  bookmark?: string;
  limit?: number;
}

export type PopularInitiatedAction = FSA<
  typeof POPULAR_INITIATED,
  PopularInitiatedActionPayload
>;

export interface PopularSuccessfulPayload {
  popular: Thing[];
}

export type PopularSuccessfulAction = FSA<
  typeof POPULAR_SUCCESSFUL,
  PopularSuccessfulPayload
>;

export type PopularFailedAction = ErrorFSA<
  Error,
  undefined,
  typeof POPULAR_FAILED
>;

export const retrievePopular = createAction<PopularInitiatedAction>(
  POPULAR_INITIATED,
);

export const popularSuccess = createAction<PopularSuccessfulAction>(
  POPULAR_SUCCESSFUL,
);

export const popularFailed = createAction<PopularFailedAction>(POPULAR_FAILED);

export const popularSaga = function*() {
  yield takeEvery(POPULAR_INITIATED, function*({
    payload,
  }: PopularInitiatedAction) {
    if (payload) {
      try {
        let response = yield clientEffect(
          client => client.getPopular,
          defaultMovieMeta,
          payload.token,
          payload.itemTypes,
          payload.network,
          payload.bookmark,
          payload.limit,
        );

        if (response.ok) {
          yield put(
            popularSuccess({
              popular: response.data.data.map(ThingFactory.create),
            }),
          );
        }
      } catch (e) {
        console.error(e);
        yield put(popularFailed(e));
      }
    }
  });
};
