import {
  SearchFailedAction,
  SearchInitiatedAction,
  SearchSuccessfulAction,
  SEARCH_FAILED,
  SEARCH_INITIATED,
  SEARCH_SUCCESSFUL,
  PEOPLE_SEARCH_FAILED,
  PEOPLE_SEARCH_INITIATED,
  PEOPLE_SEARCH_SUCCESSFUL,
  PeopleSearchFailedAction,
  PeopleSearchInitiatedAction,
  PeopleSearchSuccessfulAction,
  QuickSearchFailedAction,
  QuickSearchInitiatedAction,
  QuickSearchSuccessfulAction,
  QUICK_SEARCH_FAILED,
  QUICK_SEARCH_INITIATED,
  QUICK_SEARCH_SUCCESSFUL,
  SEARCH_PRELOAD_INITIATED,
} from '../actions/search';
import { Item } from '../types/v2/Item';
import { flattenActions, handleAction } from './utils';
import { Person } from '../types/v2/Person';

export interface State {
  currentSearchText: string;
  error: boolean;
  searching: boolean;
  results?: Item[];
  bookmark?: string;
  quick: {
    searching: boolean;
    currentSearchText: String;
    results?: Item[];
    bookmark?: string;
    error: boolean;
  };
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
  quick: {
    currentSearchText: '',
    error: false,
    searching: false,
  },
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

const preloadSearchInitiated = handleAction<SearchInitiatedAction, State>(
  SEARCH_PRELOAD_INITIATED,
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

const quickSearchFailed = handleAction<QuickSearchFailedAction, State>(
  QUICK_SEARCH_FAILED,
  (state, error) => {
    return {
      ...state,
      quick: {
        ...state.quick,
        searching: false,
        error: true,
      },
    };
  },
);

const quickSearchInitiated = handleAction<QuickSearchInitiatedAction, State>(
  QUICK_SEARCH_INITIATED,
  (state, action) => {
    return {
      ...state,
      quick: {
        ...state.quick,
        searching: true,
        currentSearchText: action.payload!.query.trim(),
      },
    };
  },
);

const quickSearchSuccess = handleAction<QuickSearchSuccessfulAction, State>(
  QUICK_SEARCH_SUCCESSFUL,
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
        quick: {
          ...state.quick,
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
  'search',
  initialState,
  quickSearchInitiated,
  quickSearchSuccess,
  quickSearchFailed,
  searchInitiated,
  preloadSearchInitiated,
  searchSuccess,
  searchFailed,
  peopleSearchInitiated,
  peopleSearchFailed,
  peopleSearchSuccess,
);
