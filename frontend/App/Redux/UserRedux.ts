import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

import { User } from '../Model';

/* ------------- Types and Action Creators ------------- */

const { Types, Creators } = createActions({
    userSignupRequest: ['componentId', 'username', 'userEmail', 'password'],
    userSignupSuccess: null,
    userSignupFailure: null,
    userSelfRequest: null,
    userRequest: ['userId'],
    userSuccess: ['user'],
    userFailure: null,
    loginRequest: [ 'componentId', 'email', 'password'],
    loginSuccess: ['token'],
    loginFailure: null
})

export const UserTypes = Types
export default Creators

/* ------------- Initial State ------------- */

export interface UserState extends Partial<User> {
    fetching: boolean,
    userId: string | number,
    token?: string,
    error: boolean,
    signup: SignupState,
    login: LoginState
}

export interface LoginState {
    fetching?: boolean,
    error?: boolean
}

export interface SignupState {
    fetching: boolean,
    success?: boolean,
    error: boolean
}

export const INITIAL_STATE = Immutable<UserState>({
    fetching: null,
    userId: null,
    error: null,
    signup: {
        fetching: null,
        error: null
    },
    login: {}
})

type State = Immutable.ImmutableObject<UserState>

export const reducers = {
    request: (state: State, { userId }: AnyAction) => 
        state.merge({ fetching: true, userId }),

    selfRequest: (state: State) =>
        state.merge({ fetching: true, userId: 'self' }),

    success: (state: State, action: AnyAction) =>
        state.merge({ fetching: false, error: null, ...action.user.data }),

    failure: (state: State) =>
        state.merge({ fetching: false, error: true }),

    signupRequest: (state: State) =>
        state.merge({ signup: { fetching: true } }),

    signupSuccess: (state: State, { token }: AnyAction) =>
        state.merge({ signup: { fetching: false, error: false, success: true }}, token),

    signupFailure: (state: State) =>
        state.merge({ signup: { fetching: false, error: true, success: false }}),

    login: (state: State) =>
        state.merge({ login: { fetching: true } }),

    loginSuccess: (state: State, { token }: AnyAction) => {
        let s = state.merge({ login: { fetching: false, error: false }, token });
        console.log(s);
        return s;
    },
        
    
    loginFailure: (state: State) =>
        state.merge({ login: { fetching: false, error: true } }),
}

export const reducer = createReducer<State>(INITIAL_STATE, {
    [Types.USER_REQUEST]: reducers.request,
    [Types.USER_SELF_REQUEST]: reducers.request,
    [Types.USER_SUCCESS]: reducers.success,
    [Types.USER_FAILURE]: reducers.failure,
    [Types.USER_SIGNUP_REQUEST]: reducers.signupRequest,
    [Types.USER_SIGNUP_SUCCESS]: reducers.signupSuccess,
    [Types.USER_SIGNUP_FAILURE]: reducers.signupFailure,
    [Types.LOGIN_REQUEST]: reducers.login,
    [Types.LOGIN_SUCCESS]: reducers.loginSuccess,
    [Types.LOGIN_FAILURE]: reducers.loginFailure
})
