import { FSA } from 'flux-standard-action';
import {
  NETWORKS_LOAD,
  NETWORKS_LOAD_SUCCESS,
  NETWORKS_LOAD_FAILED,
} from '../constants/metadata';
import { createAction, clientEffect, createBasicAction } from './utils';
import { takeEvery, put, takeLatest, select } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../utils/api-client';
import { Network } from '../types';
import { AppState } from '../reducers';

export type NetworksLoadAction = FSA<typeof NETWORKS_LOAD>;

export interface NetworkMetadataPayload {
  networks: Network[];
}

export type NetworksLoadSuccessAction = FSA<
  typeof NETWORKS_LOAD_SUCCESS,
  NetworkMetadataPayload
>;
export type NetworksLoadFailedAction = FSA<typeof NETWORKS_LOAD_FAILED, Error>;

export const loadNetworks = createBasicAction<NetworksLoadAction>(
  NETWORKS_LOAD,
);
export const loadNetworksSuccess = createAction<NetworksLoadSuccessAction>(
  NETWORKS_LOAD_SUCCESS,
);

export const loadNetworksFailed = createAction<NetworksLoadFailedAction>(
  NETWORKS_LOAD_FAILED,
);

export const loadNetworksSaga = function*() {
  yield takeLatest(NETWORKS_LOAD, function*() {
    let currentState: AppState = yield select();

    if (currentState.metadata.networks) {
      yield put(
        loadNetworksSuccess({
          networks: currentState.metadata.networks,
        }),
      );
    } else {
      try {
        let res: TeletrackerResponse<Network[]> = yield clientEffect(
          client => client.getNetworks,
        );

        yield put(
          loadNetworksSuccess({
            networks: res.data!.data,
          }),
        );
      } catch (e) {
        yield put(loadNetworksFailed(e));
      }
    }
  });
};
