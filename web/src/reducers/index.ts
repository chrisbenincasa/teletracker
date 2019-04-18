import { combineReducers } from 'redux';
import auth, { State as AuthState } from './auth';
import search, { State as SearchState } from './search';
import userSelf, { State as UserSelfState } from './user';

// A type that represents the entire app state
export interface AppState {
  auth: AuthState;
  search: SearchState;
  userSelf: UserSelfState;
}

export default combineReducers({
  auth,
  search,
  userSelf,
});
