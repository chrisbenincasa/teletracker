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
import { List, Record, RecordOf } from 'immutable';

export type StateType = {
  metadataLoading: boolean;
  networksLoading: boolean;
  genresLoading: boolean;
  networks?: List<Network>;
  genres?: List<Genre>;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {
  metadataLoading: false,
  networksLoading: false,
  genresLoading: false,
};

export const makeState = Record(initialState);

const handleNetworksInitiated = handleAction<NetworksLoadAction, State>(
  NETWORKS_LOAD,
  state => state.set('networksLoading', true),
);

const handleNetworksSuccess = handleAction<NetworksLoadSuccessAction, State>(
  NETWORKS_LOAD_SUCCESS,
  (state, { payload }) =>
    state.merge({
      networksLoading: false,
      networks: List(payload!.networks),
    }),
);

const handleGenresInitiated = handleAction<GenresLoadAction, State>(
  GENRES_LOAD,
  state => state.set('genresLoading', true),
);

const handleGenresSuccess = handleAction<GenresLoadSuccessAction, State>(
  GENRES_LOAD_SUCCESS,
  (state, { payload }) =>
    state.merge({
      genresLoading: false,
      genres: List(payload!.genres),
    }),
);

const handleMetadataInitiated = handleAction<MetadataLoadAction, State>(
  METADATA_LOAD,
  state => state.set('metadataLoading', true),
);

const handleMetadataSuccess = handleAction<MetadataLoadSuccessAction, State>(
  METADATA_LOAD_SUCCESS,
  (state, { payload }) =>
    state.merge({
      metadataLoading: false,
      genres: List(payload!.genres),
      networks: List(payload!.networks),
    }),
);

export default {
  initialState: makeState(),
  reducer: flattenActions<State>(
    'metadata',
    makeState(),
    handleNetworksInitiated,
    handleNetworksSuccess,
    handleGenresInitiated,
    handleGenresSuccess,
    handleMetadataInitiated,
    handleMetadataSuccess,
  ),
};
