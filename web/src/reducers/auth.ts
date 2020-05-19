import {
  loginInitiated,
  loginSuccessful,
  logInWithGoogle,
  logoutSuccessful,
  setToken,
  signupInitiated,
  signupSuccessful,
  unsetToken,
  userStateChange,
} from '../actions/auth';
import { User } from '../types';
import { createReducer } from '@reduxjs/toolkit';

export interface UserState extends Partial<User> {
  readonly fetching: boolean;
  readonly userId?: string;
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

export default createReducer(initialState, builder => {
  builder.addCase(userStateChange, (state, action) => {
    state.isLoggedIn = action.payload.authenticated;
    state.checkingAuth = false;
    if (!state.user) {
      state.user = {
        fetching: false,
        error: false,
        userId: action.payload.userId,
      };
    } else {
      state.user.userId = action.payload.userId;
    }
  });

  builder.addCase(signupInitiated, state => {
    state.isSigningUp = true;
  });

  builder.addCase(signupSuccessful, (state, action) => {
    state.isSigningUp = false;
    state.isLoggedIn = true;
    state.token = action.payload;
  });

  builder.addCase(loginInitiated, state => void (state.isLoggingIn = true));

  builder.addCase(logInWithGoogle, state => void (state.isLoggingIn = true));

  builder.addCase(loginSuccessful, (state, action) => {
    state.token = action.payload;
    state.isLoggingIn = false;
    state.isLoggedIn = true;
  });

  builder.addCase(logoutSuccessful, (state, action) => {
    delete state.token;
    state.isLoggedIn = false;
    state.isLoggingOut = false;
  });

  builder.addCase(setToken, (state, action) => {
    state.isLoggedIn = true;
    state.token = action.payload;
  });

  builder.addCase(unsetToken, (state, action) => {
    state.isLoggedIn = false;
    delete state.token;
  });
});
