import { Dispatch, AnyAction } from 'redux';
import { TeletrackerApi } from '../utils/api-client';
import { User } from '../Model';
import { ThunkAction } from 'redux-thunk';
import { AppState } from '.';

const client = new TeletrackerApi();

export const AUTH_CHECK = 'auth/CHECK';
export const AUTH_CHECK_INITIATED = 'auth/CHECK_INITIATED';
export const AUTH_CHECK_AUTHORIZED = 'auth/IS_AUTHED';
export const AUTH_CHECK_UNAUTH = 'auth/NOT_AUTHED';
export const AUTH_CHECK_FAILED = 'auth/CHECK_FAILED';

export const LOGIN_INITIATED = 'login/INITIATED';
export const LOGIN_SUCCESSFUL = 'login/SUCCESSFUL';

export const LOGOUT_INITIATED = 'logout/INITIATED';
export const LOGOUT_SUCCESSFUL = 'logout/SUCCESSFUL';
export const LOGOUT_FAILED = 'logout/FAILED';

interface AuthCheckInitiatedAction {
  type: typeof AUTH_CHECK_INITIATED;
}

interface AuthCheckAuthorizedAction {
  type: typeof AUTH_CHECK_AUTHORIZED;
}

interface LoginInitiatedAction {
  type: typeof LOGIN_INITIATED;
}

interface LoginSuccessfulAction {
  type: typeof LOGIN_SUCCESSFUL;
  token: string;
}

interface LogoutSuccessfulAction {
  type: typeof LOGOUT_SUCCESSFUL;
}

const authCheckInitiated: () => AuthCheckInitiatedAction = () => ({
  type: AUTH_CHECK_INITIATED,
});

const authCheckAuthorized: () => AuthCheckAuthorizedAction = () => ({
  type: AUTH_CHECK_AUTHORIZED,
});

const loginSuccessful: (token: string) => LoginSuccessfulAction = token => ({
  type: LOGIN_SUCCESSFUL,
  token,
});

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

type AuthActionTypes =
  | AuthCheckInitiatedAction
  | AuthCheckAuthorizedAction
  | LoginInitiatedAction
  | LoginSuccessfulAction
  | LogoutSuccessfulAction;

export const checkAuth = () => {
  return async (dispatch: Dispatch, getState: () => AppState) => {
    dispatch(authCheckInitiated());

    let currState = getState();

    if (currState.auth && currState.auth.token) {
      client.setToken(currState.auth.token);
      dispatch(authCheckAuthorized());
    } else {
      return client
        .getAuthStatus()
        .then(response => {
          if (response.status == 200) {
            dispatch(authCheckAuthorized());
          } else {
            dispatch({
              type: AUTH_CHECK_UNAUTH,
            });
          }
        })
        .catch(() => {
          dispatch({
            type: AUTH_CHECK_FAILED,
          });
        });
    }
  };
};

export const login = (email: string, password: string) => {
  return async (dispatch: Dispatch) => {
    const response = await client.loginUser(email, password);
    if (response.ok) {
      let token = response.data.data.token;
      client.setToken(token);
      dispatch(loginSuccessful(token));
    }
  };
};

export const logout = () => {
  return async (dispatch: Dispatch) => {
    return client
      .logoutUser()
      .then(() => {
        dispatch({
          type: LOGOUT_SUCCESSFUL,
        });
      })
      .catch(() => {});
  };
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
