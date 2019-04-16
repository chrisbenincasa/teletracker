import { combineReducers } from 'redux';
import auth, { State as AuthState } from './auth';

// A type that represents the entire app state
export interface AppState {
  auth: AuthState;
}

export default combineReducers({
  auth,
});
