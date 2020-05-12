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
import { List, Record, RecordOf } from 'immutable';

type StateType = {
  popular?: List<string>; // Array of popular slugs
  genre?: List<string>; // Array of slugs for the current genre view
  loadingPopular: boolean;
  loadingGenres: boolean;
  popularBookmark?: string;
  genreBookmark?: string;
  currentFilters?: FilterParams;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {
  loadingPopular: false,
  loadingGenres: false,
};

const makeState = Record(initialState);

const PopularInitiated = handleAction<PopularInitiatedAction, State>(
  POPULAR_INITIATED,
  (state: State) => {
    return state.set('loadingPopular', true);
  },
);

const PopularSuccess = handleAction<PopularSuccessfulAction, State>(
  POPULAR_SUCCESSFUL,
  (state: State, { payload }: PopularSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      let newPopular: List<string>;
      if (payload.append) {
        newPopular = (state.popular || List()).concat(
          R.map(t => t.id, payload.popular),
        );
      } else {
        newPopular = List(R.map(t => t.id, payload.popular));
      }

      return state.merge({
        loadingPopular: false,
        popular: newPopular,
        popularBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
        currentFilters: payload.forFilters,
      });
    } else {
      return state;
    }
  },
);

const PopularFailed = handleError<PopularFailedAction, State>(
  POPULAR_FAILED,
  (state: State, { payload }: PopularFailedAction) => {
    // TODO: Return popularity and sort by that.
    return state.set('loadingPopular', false);
  },
);

const handleClearPopular = handleAction<PopularClearAction, State>(
  POPULAR_CLEAR,
  (state: State, action: PopularClearAction) => {
    return state.merge({
      popular: undefined,
      popularBookmark: undefined,
      loadingPopular: false,
      currentFilters: undefined,
    });
  },
);

export default {
  initialState: makeState(),
  reducer: flattenActions<State>(
    'popular',
    makeState(),
    PopularInitiated,
    PopularSuccess,
    PopularFailed,
    handleClearPopular,
  ),
};
