import * as R from 'ramda';
import {
  PopularInitiatedAction,
  PopularSuccessfulAction,
  POPULAR_INITIATED,
  POPULAR_SUCCESSFUL,
  PopularFailedAction,
  POPULAR_FAILED,
} from '../actions/popular';
import {
  flattenActions,
  handleAction,
  StateToReducer,
  handleError,
} from './utils';
import { FSA } from 'flux-standard-action';

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
      };
    } else {
      return state;
    }
  },
);

const PopularFailed = handleError<PopularFailedAction, State>(
  POPULAR_FAILED,
  (state: State, { payload }: PopularFailedAction) => {
    // TODO: Return popularity and sort by that.
    console.log('failed');
    return {
      ...state,
      loadingPopular: false,
    };
  },
);

export default flattenActions<State>(
  initialState,
  PopularInitiated,
  PopularSuccess,
  PopularFailed,
);
