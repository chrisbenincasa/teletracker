import { handleAction, flattenActions } from './utils';
import {
  PopularSuccessfulAction,
  POPULAR_SUCCESSFUL,
} from '../actions/popular';
import * as R from 'ramda';
import {
  GENRE_SUCCESSFUL,
  GenreSuccessfulAction,
} from '../actions/popular/genre';

export interface State {
  popular?: string[]; // Array of popular slugs
  genre?: string[]; // Array of slugs for the current genre view
}

const initialState: State = {};

const PopularSuccess = handleAction<PopularSuccessfulAction, State>(
  POPULAR_SUCCESSFUL,
  (state: State, { payload }: PopularSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      return {
        ...state,
        popular: R.map(t => t.slug, payload.popular),
      };
    } else {
      return state;
    }
  },
);

const genreSuccess = handleAction<GenreSuccessfulAction, State>(
  GENRE_SUCCESSFUL,
  (state: State, { payload }: GenreSuccessfulAction) => {
    if (payload) {
      return {
        ...state,
        genre: R.map(t => t.slug, payload.genre),
      };
    } else {
      return state;
    }
  },
);

export default flattenActions<State>(
  initialState,
  PopularSuccess,
  genreSuccess,
);
