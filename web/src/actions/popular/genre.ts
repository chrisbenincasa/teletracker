import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory, ApiThing } from '../../types/Thing';
import { ItemTypes, Paging } from '../../types';
import { TeletrackerResponse } from '../../utils/api-client';
import _ from 'lodash';
import { Item, ItemFactory } from '../../types/v2/Item';
import { ApiItem } from '../../types/v2';

export const GENRE_INITIATED = 'genre/INITIATED';
export const GENRE_SUCCESSFUL = 'genre/SUCCESSFUL';
export const GENRE_FAILED = 'genre/FAILED';

export interface GenreInitiatedActionPayload {
  genre: string;
  thingRestrict?: ItemTypes;
  bookmark?: string;
  limit?: number;
}

export type GenreInitiatedAction = FSA<
  typeof GENRE_INITIATED,
  GenreInitiatedActionPayload
>;

export interface GenreSuccessfulPayload {
  genre: Item[];
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
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.getPopularGenre,
          payload.genre,
          payload.thingRestrict,
          payload.bookmark,
        );

        if (response.ok) {
          yield put(
            genreSuccess({
              genre: response.data!.data.map(ItemFactory.create),
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
