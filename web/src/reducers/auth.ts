import { FSA } from 'flux-standard-action';
import {
  AuthCheckInitiatedAction,
  LoginSuccessfulAction,
  LogoutSuccessfulAction,
  SetTokenAction,
  UnsetTokenAction,
} from '../actions/auth';
import {
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_UNAUTH,
  LOGIN_SUCCESSFUL,
  LOGOUT_SUCCESSFUL,
  SET_TOKEN,
  UNSET_TOKEN,
} from '../constants/auth';
import { User } from '../types';
import { AnyFSAReducer, flattenActions, handleAction } from './utils';
import { PURGE } from 'redux-persist';

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
    let isAuthed = actionType == AUTH_CHECK_AUTHORIZED;
    return {
      ...state,
      ...unsetCheckingAuth(state),
      isLoggedIn: isAuthed,
    };
  });
});

const loginSuccess = handleAction<LoginSuccessfulAction, State>(
  LOGIN_SUCCESSFUL,
  (state, action) => {
    return {
      ...state,
      token: action.payload,
      isLoggedIn: true,
    };
  },
);

const logoutSuccess = handleAction<LogoutSuccessfulAction, State>(
  LOGOUT_SUCCESSFUL,
  state => {
    return {
      ...state,
      token: undefined,
      isLoggedIn: false,
    };
  },
);

const setToken = handleAction<SetTokenAction, State>(
  SET_TOKEN,
  (state, action) => {
    if (action.payload) {
      return {
        ...state,
        isLoggedIn: true,
        token: action.payload,
      };
    } else {
      return state;
    }
  },
);

const unsetToken = handleAction<UnsetTokenAction, State>(
  UNSET_TOKEN,
  (state, action) => {
    if (action.payload) {
      return {
        ...state,
        token: undefined,
        isLoggedIn: false,
      };
    } else {
      return state;
    }
  },
);

const purge: (initialState: State) => AnyFSAReducer<any> = initialState => {
  return (state: State = initialState, action: FSA<any>) => {
    if (action.type === PURGE) {
      return initialState;
    } else {
      return state;
    }
  };
};

export default flattenActions(
  initialState,
  ...[
    authInitiated,
    ...unsetCheckingAuthReducers,
    loginSuccess,
    logoutSuccess,
    setToken,
    unsetToken,
  ],
);
