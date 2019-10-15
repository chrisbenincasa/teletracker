import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory, ApiThing } from '../../types/Thing';
import { Paging } from '../../types';
import { TeletrackerResponse } from '../../utils/api-client';
import _ from 'lodash';

export const GENRE_INITIATED = 'genre/INITIATED';
export const GENRE_SUCCESSFUL = 'genre/SUCCESSFUL';
export const GENRE_FAILED = 'genre/FAILED';

export interface GenreInitiatedActionPayload {
  genre: string;
  thingRestrict?: 'movie' | 'show';
  bookmark?: string;
  limit?: number;
}

export type GenreInitiatedAction = FSA<
  typeof GENRE_INITIATED,
  GenreInitiatedActionPayload
>;

export interface GenreSuccessfulPayload {
  genre: Thing[];
  paging?: Paging;
  append: boolean;
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
        let response: TeletrackerResponse<ApiThing[]> = yield clientEffect(
          client => client.getPopularGenre,
          payload.genre,
          payload.thingRestrict,
          payload.bookmark,
        );

        if (response.ok) {
          yield put(
            genreSuccess({
              genre: response.data!.data.map(ThingFactory.create),
              paging: response.data!.paging,
              append: !_.isUndefined(payload.bookmark),
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
