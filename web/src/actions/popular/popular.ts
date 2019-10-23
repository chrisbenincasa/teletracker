import { put, takeEvery } from '@redux-saga/core/effects';
import { ErrorFSA, FSA } from 'flux-standard-action';
import _ from 'lodash';
import { ItemTypes, Paging } from '../../types';
import { KeyMap, ObjectMetadata } from '../../types/external/themoviedb/Movie';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect, createAction } from '../utils';

export const POPULAR_INITIATED = 'popular/INITIATED';
export const POPULAR_SUCCESSFUL = 'popular/SUCCESSFUL';
export const POPULAR_FAILED = 'popular/FAILED';

export interface PopularInitiatedActionPayload {
  fields?: KeyMap<ObjectMetadata>;
  itemTypes?: ItemTypes;
  networks?: string;
  bookmark?: string;
  limit?: number;
}

export type PopularInitiatedAction = FSA<
  typeof POPULAR_INITIATED,
  PopularInitiatedActionPayload
>;

export interface PopularSuccessfulPayload {
  popular: Item[];
  paging?: Paging;
  append: boolean;
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
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.getPopular,
          payload.fields,
          payload.itemTypes,
          payload.networks,
          payload.bookmark,
          payload.limit,
        );

        if (response.ok) {
          yield put(
            popularSuccess({
              popular: response.data!.data.map(ItemFactory.create),
              paging: response.data!.paging,
              append: !_.isUndefined(payload.bookmark),
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
