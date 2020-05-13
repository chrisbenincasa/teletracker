import { FSA } from 'flux-standard-action';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { put, select, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { Genre } from '../../types';
import { AppState } from '../../reducers';

export const GENRES_LOAD = 'metadata/genres/LOAD';
export const GENRES_LOAD_SUCCESS = 'metadata/genres/SUCCESS';
export const GENRES_LOAD_FAILED = 'metadata/genres/FAILED';

export type GenresLoadAction = FSA<typeof GENRES_LOAD>;

export interface GenreMetadataPayload {
  readonly genres: ReadonlyArray<Genre>;
}

export type GenresLoadSuccessAction = FSA<
  typeof GENRES_LOAD_SUCCESS,
  GenreMetadataPayload
>;
export type GenresLoadFailedAction = FSA<typeof GENRES_LOAD_FAILED, Error>;

export const loadGenres = createBasicAction<GenresLoadAction>(GENRES_LOAD);
export const loadGenresSuccess = createAction<GenresLoadSuccessAction>(
  GENRES_LOAD_SUCCESS,
);

export const loadGenresFailed = createAction<GenresLoadFailedAction>(
  GENRES_LOAD_FAILED,
);

export const loadGenresSaga = function*() {
  yield takeLatest(GENRES_LOAD, function*() {
    let currentState: AppState = yield select();

    if (currentState.metadata.genres) {
      yield put(
        loadGenresSuccess({
          genres: currentState.metadata.genres,
        }),
      );
    } else {
      try {
        let res: TeletrackerResponse<Genre[]> = yield clientEffect(
          client => client.getGenres,
        );

        yield put(
          loadGenresSuccess({
            genres: res.data!.data,
          }),
        );
      } catch (e) {
        yield put(loadGenresFailed(e));
      }
    }
  });
};
