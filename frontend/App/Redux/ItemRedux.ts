import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    fetchMovie: ['id'],
    fetchShow: ['id']
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

export const reducers = {
    addToList,
    addToListSuccess,
    addToListFailure
};

export const reducer = createReducer<{}>(INITIAL_STATE, {
    [Types.ADD_TO_LIST]: addToList,
    [Types.ADD_TO_LIST_SUCCESS]: addToListSuccess,
    [Types.ADD_TO_LIST_FAILURE]: addToListFailure
});