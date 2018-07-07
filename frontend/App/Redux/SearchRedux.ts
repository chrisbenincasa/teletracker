import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    searchRequest: ['searchText'],
    searchClear: null,
    searchSuccess: ['response'],
    searchFailure: null
});

export const SearchTypes = Types;
export default Creators;

export interface SearchState {
    fetching?: boolean,
    searchText?: string,
    searchClear?: any[],
    results?: any[],
    error?: boolean
}

type State = Immutable.ImmutableObject<SearchState>

export const INITIAL_STATE = Immutable<SearchState>({});

const searchRequest = (state: State, { searchText }: AnyAction) => {
    return state.merge({ fetching: true, searchText });
};

const searchClear = (state: State) => {
    return state.merge({ fetching: false, searchText: null, results: undefined});
};

const searchSuccess = (state: State, { response }: AnyAction) => {
    return state.merge({ fetching: false, results: response });
};

const searchFailure = (state: State) => {
    return state.merge({ fetching: false, error: true })
}

export const reducers = {
    searchRequest,
    searchClear,
    searchSuccess,
    searchFailure
};

export const reducer = createReducer<State>(INITIAL_STATE, {
    [Types.SEARCH_REQUEST]: searchRequest,
    [Types.SEARCH_CLEAR]: searchClear,
    [Types.SEARCH_SUCCESS]: searchSuccess,
    [Types.SEARCH_FAILURE]: searchFailure
});