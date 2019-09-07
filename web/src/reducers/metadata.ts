import { Genre, Network } from '../types';

import { flattenActions, handleAction } from './utils';
import {
  NETWORKS_LOAD,
  NETWORKS_LOAD_SUCCESS,
  NetworksLoadAction,
  NetworksLoadSuccessAction,
} from '../actions/metadata';
import {
  GENRES_LOAD,
  GENRES_LOAD_SUCCESS,
  GenresLoadAction,
  GenresLoadSuccessAction,
} from '../actions/metadata/load_genres';

export interface State {
  networksLoading: boolean;
  genresLoading: boolean;
  networks?: Network[];
  genres?: Genre[];
}

const initialState: State = {
  networksLoading: false,
  genresLoading: false,
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

const handleGenresInitiated = handleAction<GenresLoadAction, State>(
  GENRES_LOAD,
  state => ({
    ...state,
    genresLoading: true,
  }),
);

const handleGenresSuccess = handleAction<GenresLoadSuccessAction, State>(
  GENRES_LOAD_SUCCESS,
  (state, { payload }) => ({
    ...state,
    genresLoading: false,
    genres: payload!.genres,
  }),
);

export default flattenActions<State>(
  initialState,
  handleNetworksInitiated,
  handleNetworksSuccess,
  handleGenresInitiated,
  handleGenresSuccess,
);
