import {
  SEARCH_FAILED,
  SEARCH_INITIATED,
  SEARCH_SUCCESSFUL,
  SearchFailedAction,
  SearchInitiatedAction,
  SearchSuccessfulAction,
} from '../actions/search';
import { Thing } from '../types';
import { flattenActions, handleAction } from './utils';

export interface State {
  currentSearchText: string;
  error: boolean;
  searching: boolean;
  results?: Thing[];
}

const initialState: State = {
  currentSearchText: '',
  error: false,
  searching: false,
};

const searchFailed = handleAction<SearchFailedAction, State>(
  SEARCH_FAILED,
  (state, error) => {
    return {
      ...state,
      searching: false,
      error: true,
    };
  },
);

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

export default flattenActions(
  initialState,
  searchInitiated,
  searchSuccess,
  searchFailed,
);
