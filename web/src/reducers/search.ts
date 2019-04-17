import { SEARCH_INITIATED, SEARCH_SUCCESSFUL } from '../constants/search';
import { SearchActionTypes } from '../actions/search';
import { Thing } from '../types/external/themoviedb/Movie';

export interface State {
  searching: boolean;
  currentSearchText: string;
  results?: Thing[];
}

const initialState: State = {
  searching: false,
  currentSearchText: '',
};

export default function searchReducer(
  state: State = initialState,
  action: SearchActionTypes,
): State {
  switch (action.type) {
    case SEARCH_INITIATED:
      return {
        ...state,
        searching: true,
        currentSearchText: action.text.trim(),
      };

    case SEARCH_SUCCESSFUL:
      return {
        ...state,
        searching: false,
        results: action.results,
      };
    default:
      return state;
  }
}
