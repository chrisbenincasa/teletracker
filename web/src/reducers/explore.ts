import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import {
  EXPLORE_INITIATED,
  EXPLORE_SUCCESSFUL,
  ExploreInitiatedAction,
  ExploreSuccessfulAction,
} from '../actions/explore';
import { List, Record, RecordOf } from 'immutable';

type StateType = {
  items?: List<string>; // Array of current slugs
  loadingExplore: boolean;
  exploreBookmark?: string;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {
  loadingExplore: false,
};

export const makeState = Record(initialState);

const exploreInitiated = handleAction<ExploreInitiatedAction, State>(
  EXPLORE_INITIATED,
  (state: State) => {
    return state.set('loadingExplore', true);
  },
);

const exploreSuccess = handleAction<ExploreSuccessfulAction, State>(
  EXPLORE_SUCCESSFUL,
  (state: State, { payload }: ExploreSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      let newItems: List<string>;
      if (payload.append) {
        newItems = (state.items || List()).concat(
          R.map(t => t.id, payload.items),
        );
      } else {
        newItems = List(payload.items).map(i => i.id);
      }

      return state.merge({
        loadingExplore: false,
        items: newItems,
        exploreBookmark: payload!.paging ? payload!.paging.bookmark : undefined,
      });
    } else {
      return state;
    }
  },
);

export default {
  initialState: makeState(),
  reducer: flattenActions<State>(
    'explore',
    makeState(),
    exploreInitiated,
    exploreSuccess,
  ),
};
