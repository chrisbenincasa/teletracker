import { Action, combineReducers } from 'redux';
import auth, { State as AuthState } from './auth';
import lists, { State as ListsState } from './lists';
import itemDetail, { State as ItemDetailState } from './item-detail';
import search, { State as SearchState } from './search';
import userSelf, { State as UserSelfState } from './user';
import metadata, { State as MetadataState } from './metadata';
import availability, { State as AvailabilityState } from './availability';
import popular, { State as PopularState } from './popular';
import people, { State as PersonState } from './people';

export interface StartupState {
  isBooting: boolean;
}

// A type that represents the entire app state
export interface AppState {
  auth: AuthState;
  itemDetail: ItemDetailState;
  search: SearchState;
  userSelf: UserSelfState;
  lists: ListsState;
  metadata: MetadataState;
  availability: AvailabilityState;
  popular: PopularState;
  people: PersonState;
  startup: StartupState;
}

// TODO clean this up - move to own file
function startupReducer(
  state: StartupState | undefined,
  action: Action,
): StartupState {
  if (!state) {
    return {
      isBooting: true,
    };
  } else {
    if (action.type === 'boot/DONE') {
      return {
        ...state,
        isBooting: false,
      };
    } else {
      return {
        ...state,
      };
    }
  }
}

export default combineReducers({
  auth,
  itemDetail,
  search,
  userSelf,
  lists,
  startup: startupReducer,
  metadata,
  availability,
  popular,
  people,
});
