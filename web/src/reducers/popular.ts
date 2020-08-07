import * as R from 'ramda';
import {
  POPULAR_CLEAR,
  POPULAR_FAILED,
  POPULAR_INITIATED,
  POPULAR_SUCCESSFUL,
  PopularClearAction,
  PopularFailedAction,
  PopularInitiatedAction,
  PopularSuccessfulAction,
} from '../actions/popular';
import { flattenActions, handleAction, handleError } from './utils';
import { FilterParams } from '../utils/searchFilters';

export interface State {
  readonly popular?: string[]; // Array of popular slugs
  readonly genre?: string[]; // Array of slugs for the current genre view
  readonly loadingPopular: boolean;
  readonly loadingGenres: boolean;
  readonly popularBookmark?: string;
  readonly genreBookmark?: string;
  readonly currentFilters?: FilterParams;
}

const initialState: State = {
  loadingPopular: false,
  loadingGenres: false,
};

const PopularInitiated = handleAction<PopularInitiatedAction, State>(
  POPULAR_INITIATED,
  (state: State) => {
    return {
      ...state,
      loadingPopular: true,
    };
  },
);

const PopularSuccess = handleAction<PopularSuccessfulAction, State>(
  POPULAR_SUCCESSFUL,
  (state: State, { payload }: PopularSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      let newPopular: string[];
      if (payload.append) {
        newPopular = (state.popular || []).concat(
          R.map(t => t.id, payload.popular),
        );
      } else {
        newPopular = R.map(t => t.id, payload.popular);
      }

      return {
        ...state,
        loadingPopular: false,
        popular: newPopular,
        popularBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
        currentFilters: payload.forFilters,
      };
    } else {
      return state;
    }
  },
);

const PopularFailed = handleError<PopularFailedAction, State>(
  POPULAR_FAILED,
  (state: State, { payload, error }: PopularFailedAction) => {
    // TODO: Return popularity and sort by that.
    console.log('failed', payload);
    return {
      ...state,
      loadingPopular: false,
    };
  },
);

const handleClearPopular = handleAction<PopularClearAction, State>(
  POPULAR_CLEAR,
  (state: State, action: PopularClearAction) => {
    return {
      ...state,
      popular: undefined,
      popularBookmark: undefined,
      loadingPopular: false,
      currentFilters: undefined,
    };
  },
);

export default flattenActions<State>(
  'popular',
  initialState,
  PopularInitiated,
  PopularSuccess,
  PopularFailed,
  handleClearPopular,
);
