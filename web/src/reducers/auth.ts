import { FSA } from 'flux-standard-action';
import {
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
  SIGNUP_INITIATED,
  SIGNUP_SUCCESSFUL,
  SignupInitiatedAction,
  SignupSuccessfulAction,
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
  isSigningUp: boolean;
  token?: string;
  user?: UserState;
}

const initialState: State = {
  checkingAuth: true,
  isLoggingIn: false,
  isLoggedIn: false,
  isLoggingOut: false,
  isSigningUp: false,
};

const signupInitiated = handleAction<SignupInitiatedAction, State>(
  SIGNUP_INITIATED,
  state => {
    return {
      ...state,
      isSigningUp: true,
    };
  },
);

const signupSuccessful = handleAction<SignupSuccessfulAction, State>(
  SIGNUP_SUCCESSFUL,
  (state, action) => {
    return {
      ...state,
      isSigningUp: false,
      isLoggingIn: true,
      token: action.payload,
    };
  },
);

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
    ...loginInitiated,
    loginSuccess,
    logoutSuccess,
    setToken,
    unsetToken,
    signupInitiated,
    signupSuccessful,
  ],
);
