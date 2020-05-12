import { Action, ReducersMapObject } from 'redux';
import auth, { State as AuthState } from './auth';
import lists, { State as ListsState } from './lists';
import itemDetail, { State as ItemDetailState } from './item-detail';
import search, { State as SearchState } from './search';
import userSelf, { State as UserSelfState } from './user';
import metadata, { State as MetadataState } from './metadata';
import availability, { State as AvailabilityState } from './availability';
import popular, { State as PopularState } from './popular';
import people, { State as PersonState } from './people';
import explore, { State as ExploreState } from './explore';
import { BOOT_DONE } from '../actions';
import { Record, RecordOf } from 'immutable';
import { FSA } from 'flux-standard-action';

export type StartupStateType = {
  isBooting: boolean;
};

const makeStartupState = Record<StartupStateType>({
  isBooting: true,
});

type StartupState = RecordOf<StartupStateType>;

// A type that represents the entire app state
export type AppStateType = {
  auth: AuthState;
  itemDetail: ItemDetailState;
  search: SearchState;
  userSelf: UserSelfState;
  lists: ListsState;
  metadata: MetadataState;
  availability: AvailabilityState;
  popular: PopularState;
  explore: ExploreState;
  people: PersonState;
  startup: StartupState;
};

export type AppState = RecordOf<AppStateType>;

const initialState: AppStateType = {
  auth: auth.initialState,
  itemDetail: itemDetail.initialState,
  search: search.initialState,
  userSelf: userSelf.initialState,
  lists: lists.initialState,
  metadata: metadata.initialState,
  availability: availability.initialState,
  popular: popular.initialState,
  explore: explore.initialState,
  people: people.initialState,
  startup: makeStartupState(),
};

const makeState = Record(initialState);

// TODO clean this up - move to own file
function startupReducer(
  state: StartupState | undefined,
  action: Action,
): StartupState {
  if (!state) {
    return makeStartupState();
  } else {
    if (action.type === BOOT_DONE) {
      return state.set('isBooting', false);
    } else {
      return state;
    }
  }
}

function combineImmutableReducers<StateType>(
  reducers: ReducersMapObject<StateType, FSA<any, any, any>>,
  getDefaultState: () => RecordOf<StateType>,
) {
  return (
    inputState: RecordOf<StateType> | undefined,
    action: any,
  ): RecordOf<StateType> => {
    return (inputState || getDefaultState()).withMutations(mutable => {
      for (let key in reducers) {
        if (reducers.hasOwnProperty(key)) {
          const reducer = reducers[key];
          const currentState = mutable.get(key);
          const nextState = reducer(currentState, action);
          mutable.set(key, nextState);
        }
      }
    });
  };
}

export default () =>
  combineImmutableReducers<AppStateType>(
    {
      auth: auth.reducer,
      availability: availability.reducer,
      itemDetail: itemDetail.reducer,
      lists: lists.reducer,
      metadata: metadata.reducer,
      people: people.reducer,
      popular: popular.reducer,
      search: search.reducer,
      startup: startupReducer,
      userSelf: userSelf.reducer,
      explore: explore.reducer,
    },
    () => makeState(),
  );
