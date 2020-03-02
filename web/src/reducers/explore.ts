import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import {
  EXPLORE_INITIATED,
  EXPLORE_SUCCESSFUL,
  ExploreInitiatedAction,
  ExploreSuccessfulAction,
} from '../actions/explore';

export interface State {
  items?: string[]; // Array of current slugs
  loadingExplore: boolean;
  exploreBookmark?: string;
}

const initialState: State = {
  loadingExplore: false,
};

const exploreInitiated = handleAction<ExploreInitiatedAction, State>(
  EXPLORE_INITIATED,
  (state: State) => {
    return {
      ...state,
      loadingExplore: true,
    };
  },
);

const exploreSuccess = handleAction<ExploreSuccessfulAction, State>(
  EXPLORE_SUCCESSFUL,
  (state: State, { payload }: ExploreSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      let newItems: string[];
      if (payload.append) {
        newItems = (state.items || []).concat(R.map(t => t.id, payload.items));
      } else {
        newItems = R.map(t => t.id, payload.items);
      }

      return {
        ...state,
        loadingExplore: false,
        items: newItems,
        exploreBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
      };
    } else {
      return state;
    }
  },
);

export default flattenActions<State>(
  'explore',
  initialState,
  exploreInitiated,
  exploreSuccess,
);
