import { FSA } from 'flux-standard-action';
import {
  LOGIN_GOOGLE_INITIATED,
  LOGIN_INITIATED,
  LOGIN_SUCCESSFUL,
  LoginSuccessfulAction,
  LOGOUT_SUCCESSFUL,
  LogoutSuccessfulAction,
  SET_TOKEN,
  SetTokenAction,
  SIGNUP_INITIATED,
  SIGNUP_SUCCESSFUL,
  SignupInitiatedAction,
  SignupSuccessfulAction,
  UNSET_TOKEN,
  UnsetTokenAction,
  USER_STATE_CHANGE,
  UserStateChangeAction,
} from '../actions/auth';
import { User } from '../types';
import { flattenActions, handleAction } from './utils';
import _ from 'lodash';
import produce, { Draft } from 'immer';

export interface UserState extends Partial<User> {
  readonly fetching: boolean;
  readonly token?: string;
  readonly error: boolean;
}

export interface State {
  readonly checkingAuth: boolean;
  readonly isLoggingIn: boolean;
  readonly isLoggedIn: boolean;
  readonly isLoggingOut: boolean;
  readonly isSigningUp: boolean;
  readonly token?: string;
  readonly user?: UserState;
}

const initialState: State = {
  checkingAuth: true,
  isLoggingIn: false,
  isLoggedIn: false,
  isLoggingOut: false,
  isSigningUp: false,
};

const stateChange = handleAction<UserStateChangeAction, State>(
  USER_STATE_CHANGE,
  produce((state, { payload }) => {
    state.isLoggedIn = !_.isUndefined(payload);
    state.checkingAuth = false;
  }),
);

const signupInitiated = handleAction<SignupInitiatedAction, State>(
  SIGNUP_INITIATED,
  produce((state: Draft<State>) => {
    state.isSigningUp = true;
  }),
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

export default flattenActions(
  'auth',
  initialState,
  ...[
    ...loginInitiated,
    stateChange,
    loginSuccess,
    logoutSuccess,
    setToken,
    unsetToken,
    signupInitiated,
    signupSuccessful,
  ],
);
