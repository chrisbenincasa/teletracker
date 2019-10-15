import * as R from 'ramda';
import {
  PopularInitiatedAction,
  PopularSuccessfulAction,
  POPULAR_INITIATED,
  POPULAR_SUCCESSFUL,
} from '../actions/popular';
import {
  GenreSuccessfulAction,
  GENRE_SUCCESSFUL,
  GENRE_INITIATED,
  GenreInitiatedAction,
} from '../actions/popular/genre';
import { flattenActions, handleAction } from './utils';

export interface State {
  popular?: string[]; // Array of popular slugs
  genre?: string[]; // Array of slugs for the current genre view
  loadingPopular: boolean;
  loadingGenres: boolean;
  popularBookmark?: string;
  genreBookmark?: string;
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

const GenresInitiated = handleAction<GenreInitiatedAction, State>(
  GENRE_INITIATED,
  (state: State) => {
    return {
      ...state,
      loadingGenres: true,
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
      let newGenre: string[];
      if (payload.append) {
        newGenre = (state.genre || []).concat(
          R.map(t => t.slug, payload.genre),
        );
      } else {
        newGenre = R.map(t => t.slug, payload.genre);
      }

      return {
        ...state,
        loadingGenres: false,
        genre: newGenre,
        genreBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
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
