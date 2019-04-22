import {
  SearchInitiatedAction,
  SearchSuccessfulAction,
} from '../actions/search';
import { SEARCH_INITIATED, SEARCH_SUCCESSFUL } from '../constants/search';
import { Thing } from '../types/external/themoviedb/Movie';
import { flattenActions, handleAction } from './utils';

export interface State {
  searching: boolean;
  currentSearchText: string;
  results?: Thing[];
}

const initialState: State = {
  searching: false,
  currentSearchText: '',
};

const searchInitiated = handleAction<SearchInitiatedAction, State>(
  SEARCH_INITIATED,
  (state, action) => {
    return {
      ...state,
      searching: true,
      currentSearchText: action.payload!.trim(),
    };
  },
);

const searchSuccess = handleAction<SearchSuccessfulAction, State>(
  SEARCH_SUCCESSFUL,
  (state, { payload }) => {
    return {
      ...state,
      searching: false,
      results: payload,
    };
  },
);

export default flattenActions(initialState, searchInitiated, searchSuccess);
