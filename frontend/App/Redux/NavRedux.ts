import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';
import { AnyAction } from 'redux';

const { Types, Creators } = createActions({
  pushState: ['componentId', 'view', 'props'],
  popState: null
});

export const NavTypes = Types
export default Creators

export interface NavigationState {
  componentId?: string
};

type State = Immutable.ImmutableObject<NavigationState>;

export const INITIAL_STATE = Immutable<NavigationState>({});

const pushState = (state: State, { componentId }: AnyAction) => {
  return state.merge({});
}

const popState = (state: State) => {
  return state.merge({});
}

export const reducers = {
  pushState,
  popState
}

export const reducer = createReducer(INITIAL_STATE, {
  [Types.PUSH_STATE]: pushState,
  [Types.POP_STATE]: popState
});
