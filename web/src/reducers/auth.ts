import { FSA } from 'flux-standard-action';
import {
  AUTH_CHECK_AUTHORIZED,
  AUTH_CHECK_FAILED,
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_UNAUTH,
  AuthCheckInitiatedAction,
  LOGIN_GOOGLE_INITIATED,
  LOGIN_INITIATED,
  LOGIN_SUCCESSFUL,
  LoginSuccessfulAction,
  LOGOUT_INITIATED,
  LOGOUT_SUCCESSFUL,
  LogoutInitiatedAction,
  LogoutSuccessfulAction,
  SET_TOKEN,
  SetTokenAction,
  UNSET_TOKEN,
  UnsetTokenAction,
} from '../actions/auth';
import { User } from '../types';
import { AnyFSAReducer, flattenActions, handleAction } from './utils';
import { PURGE } from 'redux-persist';

export interface UserState extends Partial<User> {
  fetching: boolean;
  token?: string;
  error: boolean;
}

export interface State {
  checkingAuth: boolean;
  isLoggingIn: boolean;
  isLoggedIn: boolean;
  isLoggingOut: boolean;
  token?: string;
  user?: UserState;
}

const initialState: State = {
  checkingAuth: true,
  isLoggingIn: false,
  isLoggedIn: false,
  isLoggingOut: false,
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

const loginInitiated = [LOGIN_INITIATED, LOGIN_GOOGLE_INITIATED].map(
  actionType => {
    return handleAction<FSA<typeof actionType>, State>(actionType, state => {
      return {
        ...state,
        isLoggingIn: true,
      };
    });
  },
);

const loginSuccess = handleAction<LoginSuccessfulAction, State>(
  LOGIN_SUCCESSFUL,
  (state, action) => {
    return {
      ...state,
      token: action.payload,
      isLoggingIn: false,
      isLoggedIn: true,
    };
  },
);

const logoutInitiated = handleAction<LogoutInitiatedAction, State>(
  LOGOUT_INITIATED,
  state => {
    return {
      ...state,
      isLoggingOut: true,
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
      isLoggingOut: false,
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
    ...loginInitiated,
    loginSuccess,
    logoutSuccess,
    setToken,
    unsetToken,
  ],
);
