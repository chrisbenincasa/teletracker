import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

import { User } from '../Model';
import { persistReducer } from 'redux-persist';
import { AsyncStorage } from 'react-native';
import ImmutablePersistenceTransform from '../Services/ImmutablePersistenceTransform';
import R from 'ramda';

/* ------------- Types and Action Creators ------------- */

const { Types, Creators } = createActions({
    userSignupRequest: ['componentId', 'username', 'userEmail', 'password'],
    userSignupSuccess: ['token'],
    userSignupFailure: null,
    userSelfRequest: ['componentId'],
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
    token?: string,
    error: boolean,
    signup: SignupState,
    login: LoginState,
    details: User
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
    error: null,
    signup: {
        fetching: null,
        error: null
    },
    login: {},
    details: null
})

type State = Immutable.ImmutableObject<UserState>

export const reducers = {
    request: (state: State, action: AnyAction) => 
        state.merge({ fetching: true }),

    selfRequest: (state: State) =>
        state.merge({ fetching: true }),

    success: (state: State, action: AnyAction) =>
        state.merge({ fetching: false, error: null, details: action.user.data }),

    failure: (state: State) =>
        state.merge({ fetching: false, error: true }),

    signupRequest: (state: State) => {
        console.tron.log(R.has('asMutable')(state));
        return state.merge({ signup: { fetching: true } })
    },

    signupSuccess: (state: State, { token }: AnyAction) => 
        state.merge({ signup: { fetching: false, error: false, success: true }, token}),

    signupFailure: (state: State) =>
        state.merge({ signup: { fetching: false, error: true, success: false }}),

    login: (state: State) =>
        state.merge({ login: { fetching: true } }),

    loginSuccess: (state: State, { token }: AnyAction) =>
        state.merge({ login: { fetching: false, error: false }, token }),
    
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
});

// export const reducer = persistReducer({
//     key: 'user',
//     blacklist: ['signup'],
//     storage: AsyncStorage,
//     transforms: [ImmutablePersistenceTransform]
// }, baseReducer);
