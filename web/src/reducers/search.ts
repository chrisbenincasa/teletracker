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
import {
  PEOPLE_SEARCH_FAILED,
  PEOPLE_SEARCH_INITIATED,
  PEOPLE_SEARCH_SUCCESSFUL,
  PeopleSearchFailedAction,
  PeopleSearchInitiatedAction,
  PeopleSearchSuccessfulAction,
} from '../actions/search/person_search';
import { Person } from '../types/v2/Person';

export interface State {
  currentSearchText: string;
  error: boolean;
  searching: boolean;
  results?: Item[];
  bookmark?: string;
  people: {
    searching: boolean;
    currentSearchText: String;
    results?: Person[];
    bookmark?: string;
    error: boolean;
  };
}

const initialState: State = {
  currentSearchText: '',
  error: false,
  searching: false,
  people: {
    currentSearchText: '',
    error: false,
    searching: false,
  },
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

const peopleSearchFailed = handleAction<PeopleSearchFailedAction, State>(
  PEOPLE_SEARCH_FAILED,
  (state, error) => {
    return {
      ...state,
      people: {
        ...state.people,
        searching: false,
        error: true,
      },
    };
  },
);

const peopleSearchInitiated = handleAction<PeopleSearchInitiatedAction, State>(
  PEOPLE_SEARCH_INITIATED,
  (state, action) => {
    return {
      ...state,
      people: {
        ...state.people,
        searching: true,
        currentSearchText: action.payload!.query.trim(),
      },
    };
  },
);

const peopleSearchSuccess = handleAction<PeopleSearchSuccessfulAction, State>(
  PEOPLE_SEARCH_SUCCESSFUL,
  (state, { payload }) => {
    if (payload) {
      let newResults = state.people.results ? state.people.results : [];
      if (!payload.append) {
        newResults = payload.results;
      } else {
        newResults = newResults.concat(payload.results);
      }

      return {
        ...state,
        people: {
          ...state.people,
          searching: false,
          results: newResults,
          bookmark: payload.paging ? payload.paging.bookmark : undefined,
        },
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
  peopleSearchInitiated,
  peopleSearchFailed,
  peopleSearchSuccess,
);
