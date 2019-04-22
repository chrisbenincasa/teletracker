import { FSA } from 'flux-standard-action';
import {
  AuthCheckInitiatedAction,
  LoginSuccessfulAction,
  LogoutSuccessfulAction,
} from '../actions/auth';
import {
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_UNAUTH,
  LOGIN_SUCCESSFUL,
  LOGOUT_SUCCESSFUL,
} from '../constants/auth';
import { User } from '../types';
import { flattenActions, handleAction } from './utils';

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

const authInitiated = handleAction<AuthCheckInitiatedAction, State>(
  AUTH_CHECK_INITIATED,
  state => {
    return {
      ...state,
      checkingAuth: true,
    };
  },
);

const unsetCheckingAuth = (state: State) => {
  return {
    ...state,
    checkingAuth: false,
  };
};

const unsetCheckingAuthReducers = [
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_UNAUTH,
].map(actionType => {
  return handleAction<FSA<typeof actionType>, State>(actionType, state => {
    return unsetCheckingAuth(state);
  });
});

const loginSuccess = handleAction<LoginSuccessfulAction, State>(
  LOGIN_SUCCESSFUL,
  (state, action) => {
    return {
      ...state,
      token: action.payload,
    };
  },
);

const logoutSuccess = handleAction<LogoutSuccessfulAction, State>(
  LOGOUT_SUCCESSFUL,
  state => {
    return {
      ...state,
      token: undefined,
    };
  },
);

export default flattenActions(
  initialState,
  ...[authInitiated, ...unsetCheckingAuthReducers, loginSuccess, logoutSuccess],
);
