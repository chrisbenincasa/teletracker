import { Action, combineReducers } from 'redux';
import auth, { State as AuthState } from './auth';
import lists, { State as ListsState } from './lists';
import search, { State as SearchState } from './search';
import userSelf, { State as UserSelfState } from './user';

// A type that represents the entire app state
export interface AppState {
  auth: AuthState;
  search: SearchState;
  userSelf: UserSelfState;
  lists: ListsState;
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
  search,
  userSelf,
  lists,
  startup: startupReducer,
});
