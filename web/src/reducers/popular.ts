import { Thing } from '../types';
import { handleAction, flattenActions } from './utils';
import {
  PopularSuccessfulAction,
  POPULAR_SUCCESSFUL,
} from '../actions/popular';

export interface State {
  popular?: any;
}

const initialState: State = {};

const PopularSuccess = handleAction<PopularSuccessfulAction, State>(
  POPULAR_SUCCESSFUL,
  (state: State, { payload }: PopularSuccessfulAction) => {
    if (payload) {
      return {
        ...state,
        ...payload,
      };
    } else {
      return state;
    }
  },
);

export default flattenActions<State>(initialState, PopularSuccess);
