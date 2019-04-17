import { AuthActionTypes } from '../actions/auth';
import {
  LOGIN_SUCCESSFUL,
  LOGOUT_SUCCESSFUL,
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_UNAUTH,
} from '../constants/auth';
import { User } from '../types';

export interface UserState extends Partial<User> {
  fetching: boolean;
  token?: string;
  error: boolean;
  // signup: SignupState,
  // login: LoginState,
  details: User;
}

export interface State {
  checkingAuth: boolean;
  isLoggedIn: boolean;
  token?: string;
  user?: UserState;
}

const initialState: State = {
  checkingAuth: true,
  isLoggedIn: false,
};

export default function authReducer(
  state = initialState,
  action: AuthActionTypes,
): State {
  switch (action.type) {
    case AUTH_CHECK_INITIATED:
      return {
        ...state,
        checkingAuth: true,
      };

    case AUTH_CHECK_AUTHORIZED:
    case AUTH_CHECK_FAILED:
    case AUTH_CHECK_UNAUTH:
      console.log('authorized');

      return {
        ...state,
        checkingAuth: false,
      };

    case LOGIN_SUCCESSFUL:
      return {
        ...state,
        token: action.token,
      };

    case LOGOUT_SUCCESSFUL:
      return {
        ...state,
        token: undefined,
      };

    default:
      return state;
  }
}
