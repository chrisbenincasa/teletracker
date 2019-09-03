import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { defaultMovieMeta } from '../lists';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory } from '../../types/Thing';

export const POPULAR_INITIATED = 'popular/INITIATED';
export const POPULAR_SUCCESSFUL = 'popular/SUCCESSFUL';
export const POPULAR_FAILED = 'popular/FAILED';

export type PopularInitiatedAction = FSA<typeof POPULAR_INITIATED>;

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

export const retrievePopular = createBasicAction<PopularInitiatedAction>(
  POPULAR_INITIATED,
);

export const popularSuccess = createAction<PopularSuccessfulAction>(
  POPULAR_SUCCESSFUL,
);

export const popularFailed = createAction<PopularFailedAction>(POPULAR_FAILED);

export const popularSaga = function*() {
  yield takeEvery(POPULAR_INITIATED, function*() {
    try {
      let response = yield clientEffect(
        client => client.getPopular,
        undefined,
        defaultMovieMeta,
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
  });
};
