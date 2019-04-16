import { AuthActionTypes } from '../actions/auth';
import { LOGIN_SUCCESSFUL, LOGOUT_SUCCESSFUL } from '../constants/auth';
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
  isLoggedIn: boolean;
  token?: string;
  user?: UserState;
}

const initialState: State = {
  isLoggedIn: false,
};

export default function authReducer(
  state = initialState,
  action: AuthActionTypes,
): State {
  switch (action.type) {
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
