import { FSA } from 'flux-standard-action';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { put, select, takeLatest } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { Network } from '../../types';
import { AppState } from '../../reducers';
import { List } from 'immutable';

export const NETWORKS_LOAD = 'metadata/networks/LOAD';
export const NETWORKS_LOAD_SUCCESS = 'metadata/networks/SUCCESS';
export const NETWORKS_LOAD_FAILED = 'metadata/networks/FAILED';

export type NetworksLoadAction = FSA<typeof NETWORKS_LOAD>;

export interface NetworkMetadataPayload {
  networks: List<Network>;
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
            networks: List(res.data!.data),
          }),
        );
      } catch (e) {
        yield put(loadNetworksFailed(e));
      }
    }
  });
};
