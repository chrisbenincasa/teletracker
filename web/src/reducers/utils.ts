import { FSA, ErrorFSA } from 'flux-standard-action';

type AllFSA = FSA<any, any, any>;

export type FSAReducer<State, AllFSA> = (
  state: State | undefined,
  action: AllFSA,
) => State | undefined;

export type AnyFSAReducer<State> = FSAReducer<State, AllFSA>;

export function handleAction<Action extends AllFSA, State>(
  actionType: Action['type'],
  reducer: (state: State, action: Action) => State,
): (initialState: State) => FSAReducer<State, Action> {
  return (initialState: State) => {
    return (state: State = initialState, action: AllFSA) => {
      if (!action.type || action.type !== actionType) {
        return;
      } else {
        return reducer(state, action as Action);
      }
    };
  };
}

export function handleError<Action extends AllFSA, State>(
  actionType: Action['type'],
  reducer: (state: State, action: AllFSA) => State,
): (initialState: State) => FSAReducer<State, AllFSA> {
  return (initialState: State) => {
    return (state: State = initialState, action: AllFSA) => {
      if (!action.type || action.type !== actionType) {
        return;
      } else {
        return reducer(state, action);
      }
    };
  };
}

export type StateToReducer<State> = (
  initialState: State,
) => AnyFSAReducer<State>;

export function flattenActions<State>(
  initialState: State,
  ...reducers: StateToReducer<State>[]
) {
  let reducersWithState = reducers.map(r => r(initialState));
  return (state: State = initialState, action: AllFSA) => {
    let newState: State | undefined;
    reducersWithState.some(reducer => {
      let res = reducer(state, action);
      newState = res;
      return !!res;
    });

    return !newState ? state : newState;
  };
}
