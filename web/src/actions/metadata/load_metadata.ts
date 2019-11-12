import { FSA } from 'flux-standard-action';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { put, select, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { Genre, MetadataResponse, Network } from '../../types';
import { AppState } from '../../reducers';

export const METADATA_LOAD = 'metadata/all/LOAD';
export const METADATA_LOAD_SUCCESS = 'metadata/all/SUCCESS';
export const METADATA_LOAD_FAILED = 'metadata/all/FAILED';

export type MetadataLoadAction = FSA<typeof METADATA_LOAD>;

export interface MetadataPayload {
  genres: Genre[];
  networks: Network[];
}

export type MetadataLoadSuccessAction = FSA<
  typeof METADATA_LOAD_SUCCESS,
  MetadataPayload
>;
export type MetadataLoadFailedAction = FSA<typeof METADATA_LOAD_FAILED, Error>;

export const loadMetadata = createBasicAction<MetadataLoadAction>(
  METADATA_LOAD,
);
export const loadMetadataSuccess = createAction<MetadataLoadSuccessAction>(
  METADATA_LOAD_SUCCESS,
);

export const loadMetadataFailed = createAction<MetadataLoadFailedAction>(
  METADATA_LOAD_FAILED,
);

export const loadMetadataSaga = function*() {
  yield takeLatest(METADATA_LOAD, function*() {
    let currentState: AppState = yield select();

    if (currentState.metadata.genres && currentState.metadata.networks) {
      yield put(
        loadMetadataSuccess({
          genres: currentState.metadata.genres,
          networks: currentState.metadata.networks,
        }),
      );
    } else {
      try {
        let res: TeletrackerResponse<MetadataResponse> = yield clientEffect(
          client => client.getMetadata,
        );

        yield put(
          loadMetadataSuccess({
            genres: res.data!.data.genres,
            networks: res.data!.data.networks,
          }),
        );
      } catch (e) {
        yield put(loadMetadataFailed(e));
      }
    }
  });
};
