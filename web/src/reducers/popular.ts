import { Thing } from '../types';
import { handleAction, flattenActions } from './utils';
import {
  PopularSuccessfulAction,
  POPULAR_SUCCESSFUL,
} from '../actions/popular';
import * as R from 'ramda';

export interface State {
  popular?: string[]; // Array of popular slugs
}

const initialState: State = {};

const PopularSuccess = handleAction<PopularSuccessfulAction, State>(
  POPULAR_SUCCESSFUL,
  (state: State, { payload }: PopularSuccessfulAction) => {
    // TODO: Return popularity and sort by that.
    if (payload) {
      return {
        ...state,
        popular: R.map(R.prop('normalizedName'), payload.popular),
      };
    } else {
      return state;
    }
  },
);

export default flattenActions<State>(initialState, PopularSuccess);
