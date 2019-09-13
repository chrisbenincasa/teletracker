import {
  SearchFailedAction,
  SearchInitiatedAction,
  SearchSuccessfulAction,
  SEARCH_FAILED,
  SEARCH_INITIATED,
  SEARCH_SUCCESSFUL,
} from '../actions/search';
import { Item } from '../types/v2/Item';
import { flattenActions, handleAction } from './utils';

export interface State {
  currentSearchText: string;
  error: boolean;
  searching: boolean;
  results?: Item[];
  bookmark?: string;
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
      currentSearchText: action.payload!.query.trim(),
    };
  },
);

const searchSuccess = handleAction<SearchSuccessfulAction, State>(
  SEARCH_SUCCESSFUL,
  (state, { payload }) => {
    if (payload) {
      let newResults = state.results ? state.results : [];
      if (!payload.append) {
        newResults = payload.results;
      } else {
        newResults = newResults.concat(payload.results);
      }

      return {
        ...state,
        searching: false,
        results: newResults,
        bookmark: payload.paging ? payload.paging.bookmark : undefined,
      };
    } else {
      return state;
    }
  },
);

export default flattenActions(
  initialState,
  searchInitiated,
  searchSuccess,
  searchFailed,
);
