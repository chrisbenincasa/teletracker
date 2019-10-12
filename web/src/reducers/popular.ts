import { handleAction, flattenActions } from './utils';
import {
  PopularSuccessfulAction,
  POPULAR_SUCCESSFUL,
  PopularInitiatedAction,
  POPULAR_INITIATED,
} from '../actions/popular';
import * as R from 'ramda';
import {
  GENRE_SUCCESSFUL,
  GenreSuccessfulAction,
} from '../actions/popular/genre';
import Thing from '../types/Thing';

export interface State {
  popular?: string[]; // Array of popular slugs
  genre?: string[]; // Array of slugs for the current genre view
  loadingPopular: boolean;
  popularBookmark?: string;
}

const initialState: State = {
  loadingPopular: true,
};

const PopularInitiated = handleAction<PopularInitiatedAction, State>(
  POPULAR_INITIATED,
  (state: State, { payload }: PopularInitiatedAction) => {
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
          R.map(t => t.slug, payload.popular),
        );
      } else {
        newPopular = R.map(t => t.slug, payload.popular);
      }

      return {
        ...state,
        loadingPopular: false,
        popular: newPopular,
        popularBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
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
  PopularInitiated,
  PopularSuccess,
  genreSuccess,
);
