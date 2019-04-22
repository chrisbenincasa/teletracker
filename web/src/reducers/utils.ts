import { FSA } from 'flux-standard-action';

export type FSAReducer<S, Action extends FSA<any, any, any>> = (
  state: S | undefined,
  action: Action,
) => S | undefined;

type AnyFSAReducer<S> = FSAReducer<S, FSA<any, any, any>>;

export function handleAction<Action extends FSA<any, any, any>, S>(
  actionType: Action['type'],
  reducer: (state: S, action: Action) => S,
): (initialState: S) => FSAReducer<S, Action> {
  return (initialState: S) => {
    return (state: S = initialState, action: FSA<any, any, any>) => {
      if (!action.type || action.type !== actionType) {
        return;
      } else {
        return reducer(state, action as Action);
      }
    };
  };
}

type StateToReducer<S> = (initialState: S) => AnyFSAReducer<S>;

export function flattenActions<S>(
  initialState: S,
  ...reducers: (StateToReducer<S>)[]
) {
  let reducersWithState = reducers.map(r => r(initialState));
  return (state: S = initialState, action: FSA<any, any>) => {
    let newState: S | undefined;
    reducersWithState.some(reducer => {
      let res = reducer(state, action);
      newState = res;
      return !!res;
    });

    return !newState ? state : newState;
  };
}
