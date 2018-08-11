import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

import { User } from '../Model';
import _ from 'lodash';
import { Thing } from '../Model/external/themoviedb';

/* ------------- Types and Action Creators ------------- */

const { Types, Creators } = createActions({
    userSignupRequest: ['username', 'userEmail', 'password'],
    userSignupSuccess: ['token'],
    userSignupFailure: null,
    userSelfRequest: ['componentId'],
    userRequest: ['userId'],
    userSuccess: ['user'],
    userFailure: null,
    userLogout: null,
    loginRequest: [ 'componentId', 'email', 'password'],
    loginSuccess: ['token'],
    loginFailure: null,
    logoutRequest: ['componentId'],
    logoutSuccess: null,
    postEvent: ['componentId', 'eventType', 'targetType', 'targetId'],
    appendList: ['list'],
    updateLists: ['thing', 'listsToAdd', 'listsToRemove']
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
    request: (state: State) => 
        state.merge({ fetching: true, error: null }),

    selfRequest: (state: State) =>
        state.merge({ fetching: true, error: null }),

    success: (state: State, action: AnyAction) =>
        state.merge({ fetching: false, error: null, details: action.user.data }),

    failure: (state: State) =>
        state.merge({ fetching: false, error: true }),

    signupRequest: (state: State) => 
        state.merge({ signup: { fetching: true, error: null } }),

    signupSuccess: (state: State, { token }: AnyAction) => 
        state.merge({ signup: { fetching: false, error: false, success: true }, token}),

    signupFailure: (state: State) =>
        state.merge({ signup: { fetching: false, error: true, success: false }}),

    login: (state: State) =>
        state.merge({ login: { fetching: true, error: null } }),

    logout: (state: State) =>
        state.merge({ token: null, details: null, error: null }),

    loginSuccess: (state: State, { token }: AnyAction) =>
        state.merge({ login: { fetching: false, error: false }, token }),
    
    loginFailure: (state: State) =>
        state.merge({ login: { fetching: false, error: true } }),

    logoutSuccess: (state: State) =>
        state.merge({ login: { fetching: false, error: false }, token: null, details: null }),

    appendList: (state: State, { list }: AnyAction) =>
        state.merge({ details: { lists: state.details.lists.concat([list]) } }),

    updateLists: (state: State, { thing, listsToAdd, listsToRemove }: AnyAction) => {
        let lists = state.details.lists || [];

        let newLists = lists.map(l => {
            if (_.includes(listsToAdd, l.id.toString()) && !_.find(l.things, { id: thing.id })) {
                l.things = l.things.concat([thing]);
                return l;
            } else if (_.includes(listsToRemove, l.id.toString()) && _.find(l.things, { id: thing.id })) {
                let newthings: Thing[] = _.reject(l.things, t => t.id === thing.id);
                return l.merge({ things: newthings });
            } else {
                return l
            }
        });

        return state.merge({ details: { lists: newLists }});
    },

    postEvent: (state: State) => 
        state,
}

export const reducer = createReducer<State>(INITIAL_STATE, {
    [Types.USER_REQUEST]: reducers.request,
    [Types.USER_SELF_REQUEST]: reducers.request,
    [Types.USER_SUCCESS]: reducers.success,
    [Types.USER_FAILURE]: reducers.failure,
    [Types.USER_LOGOUT]: reducers.logout,
    [Types.USER_SIGNUP_REQUEST]: reducers.signupRequest,
    [Types.USER_SIGNUP_SUCCESS]: reducers.signupSuccess,
    [Types.USER_SIGNUP_FAILURE]: reducers.signupFailure,
    [Types.LOGIN_REQUEST]: reducers.login,
    [Types.LOGIN_SUCCESS]: reducers.loginSuccess,
    [Types.LOGIN_FAILURE]: reducers.loginFailure,
    [Types.POST_EVENT]: reducers.postEvent,
    [Types.APPEND_LIST]: reducers.appendList,
    [Types.UPDATE_LISTS]: reducers.updateLists,
});

// export const reducer = persistReducer({
//     key: 'user',
//     blacklist: ['signup'],
//     storage: AsyncStorage,
//     transforms: [ImmutablePersistenceTransform]
// }, baseReducer);
