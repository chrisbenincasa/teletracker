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
import {
  METADATA_LOAD,
  METADATA_LOAD_SUCCESS,
  MetadataLoadAction,
  MetadataLoadSuccessAction,
} from '../actions/metadata/load_metadata';

export interface State {
  readonly metadataLoading: boolean;
  readonly networksLoading: boolean;
  readonly genresLoading: boolean;
  readonly networks?: Network[];
  readonly genres?: Genre[];
}

const initialState: State = {
  metadataLoading: false,
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

const handleMetadataInitiated = handleAction<MetadataLoadAction, State>(
  METADATA_LOAD,
  state => ({
    ...state,
    metadataLoading: true,
  }),
);

const handleMetadataSuccess = handleAction<MetadataLoadSuccessAction, State>(
  METADATA_LOAD_SUCCESS,
  (state, { payload }) => ({
    ...state,
    metadataLoading: false,
    genres: payload!.genres,
    networks: payload!.networks,
  }),
);

export default flattenActions<State>(
  'metadata',
  initialState,
  handleNetworksInitiated,
  handleNetworksSuccess,
  handleGenresInitiated,
  handleGenresSuccess,
  handleMetadataInitiated,
  handleMetadataSuccess,
);
