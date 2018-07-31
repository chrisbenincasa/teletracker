import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    addToList: ['componentId', 'listId', 'itemId'],
    addToListSuccess: ['response'],
    addToListFailure: null,
    createList: ['name'],
    createListSuccess: null,
    updateListTracking: ['thing', 'adds', 'removes'],
    updateListTrackingComplete: null
});

export const ListTypes = Types;
export default Creators;

export interface ListState {
    actionInProgress?: boolean,
    error?: boolean
}

type State = Immutable.ImmutableObject<ListState>

export const INITIAL_STATE = Immutable<ListState>({});

const addToList = (state: State, action: AnyAction) => {
    return state.merge({ actionInProgress: true });
};

const addToListSuccess = (state: State, action: AnyAction) => {
    return state.merge({ actionInProgress: false });
};

const addToListFailure = (state: State) => {
    return state.merge({ actionInProgress: false, error: true })
}

const createList = (state: State, action: AnyAction) => {
    return state.merge({ actionInProgress: true })
}

const createListSuccess = (state: State, action: AnyAction) => {
    return state.merge({ actionInProgress: false })
}

const updateListTracking = (state: State) => {
    return state.merge({ actionInProgress: true });
}

const updateListTrackingComplete = (state: State) => {
    return state.merge({ actionInProgress: false });
}

export const reducers = {
    addToList,
    addToListSuccess,
    addToListFailure,
    createList,
    updateListTracking,
    updateListTrackingComplete
};

export const reducer = createReducer<{}>(INITIAL_STATE, {
    [Types.ADD_TO_LIST]: addToList,
    [Types.ADD_TO_LIST_SUCCESS]: addToListSuccess,
    [Types.ADD_TO_LIST_FAILURE]: addToListFailure,
    [Types.CREATE_LIST]: createList,
    [Types.CREATE_LIST_SUCCESS]: createListSuccess,
    [Types.UPDATE_LIST_TRACKING]: updateListTracking,
    [Types.UPDATE_LIST_TRACKING_COMPLETE]: updateListTrackingComplete
});