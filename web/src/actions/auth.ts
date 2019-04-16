import {
  AUTH_CHECK_INITIATED,
  AUTH_CHECK_AUTHORIZED,
  LOGIN_INITIATED,
  LOGIN_SUCCESSFUL,
  LOGOUT_SUCCESSFUL,
  AUTH_CHECK_UNAUTH,
  AUTH_CHECK_FAILED,
} from '../constants/auth';
import { Dispatch } from 'redux';
import { TeletrackerApi } from '../utils/api-client';
import { AppState } from '../reducers';

const client = TeletrackerApi.instance;

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

export const authCheckInitiated: () => AuthCheckInitiatedAction = () => ({
  type: AUTH_CHECK_INITIATED,
});

export const authCheckAuthorized: () => AuthCheckAuthorizedAction = () => ({
  type: AUTH_CHECK_AUTHORIZED,
});

export const loginSuccessful: (
  token: string,
) => LoginSuccessfulAction = token => ({
  type: LOGIN_SUCCESSFUL,
  token,
});

export type AuthActionTypes =
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
