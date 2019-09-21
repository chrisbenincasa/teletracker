import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory } from '../../types/Thing';

export const GENRE_INITIATED = 'genre/INITIATED';
export const GENRE_SUCCESSFUL = 'genre/SUCCESSFUL';
export const GENRE_FAILED = 'genre/FAILED';

export interface GenreInitiatedActionPayload {
  genre: string;
  thingRestrict?: 'movie' | 'show';
}

export type GenreInitiatedAction = FSA<
  typeof GENRE_INITIATED,
  GenreInitiatedActionPayload
>;

export interface GenreSuccessfulPayload {
  genre: Thing[];
}

export type GenreSuccessfulAction = FSA<
  typeof GENRE_SUCCESSFUL,
  GenreSuccessfulPayload
>;

export type GenreFailedAction = ErrorFSA<Error, undefined, typeof GENRE_FAILED>;

export const retrieveGenre = createAction<GenreInitiatedAction>(
  GENRE_INITIATED,
);

export const genreSuccess = createAction<GenreSuccessfulAction>(
  GENRE_SUCCESSFUL,
);

export const genreFailed = createAction<GenreFailedAction>(GENRE_FAILED);

export const genreSaga = function*() {
  yield takeEvery(GENRE_INITIATED, function*({
    payload,
  }: GenreInitiatedAction) {
    if (payload) {
      try {
        let response = yield clientEffect(
          client => client.getPopularGenre,
          payload.genre,
          payload.thingRestrict,
        );

        if (response.ok) {
          yield put(
            genreSuccess({
              genre: response.data.data.map(ThingFactory.create),
            }),
          );
        }
      } catch (e) {
        console.error(e);
        yield put(genreFailed(e));
      }
    }
  });
};
