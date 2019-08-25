import { Action, combineReducers } from 'redux';
import auth, { State as AuthState } from './auth';
import lists, { State as ListsState } from './lists';
import itemDetail, { State as ItemDetailState } from './item-detail';
import search, { State as SearchState } from './search';
import userSelf, { State as UserSelfState } from './user';
import metadata, { State as MetadataState } from './metadata';
import availability, { State as AvailabilityState } from './availability';
import popular, { State as PopularState } from './popular';

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
}

function startupReducer(state: any | undefined, action: Action): any {
  if (!state) {
    return {};
  } else {
    return state;
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
});
