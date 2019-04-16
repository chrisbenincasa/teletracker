import { combineReducers } from 'redux';
import counter from './counter';
import auth, { State as AuthState } from './auth';

export interface AppState {
  auth: AuthState;
}

export default combineReducers({
  counter,
  auth,
});
