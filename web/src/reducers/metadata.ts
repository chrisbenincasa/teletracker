import { Network } from '../types';

import { flattenActions, handleAction } from './utils';
import {
  NETWORKS_LOAD,
  NETWORKS_LOAD_SUCCESS,
  NetworksLoadAction,
  NetworksLoadSuccessAction,
} from '../actions/metadata';

export interface State {
  networksLoading: boolean;
  networks?: Network[];
}

const initialState: State = {
  networksLoading: false,
};

const handleNetworksInitiated = handleAction<NetworksLoadAction, State>(
  NETWORKS_LOAD,
  state => ({
    ...state,
    networksLoading: true,
  }),
);

const handleNetworksSuccess = handleAction<NetworksLoadSuccessAction, State>(
  NETWORKS_LOAD_SUCCESS,
  (state, { payload }) => ({
    ...state,
    networksLoading: false,
    networks: payload!.networks,
  }),
);

export default flattenActions<State>(
  initialState,
  handleNetworksInitiated,
  handleNetworksSuccess,
);
