import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    searchRequest: ['searchText'],
    searchClear: null,
    searchAddRecent: ['item'],
    searchRemoveRecent: ['item'],
    searchRemoveAllRecent: null,
    searchSuccess: ['response'],
    searchFailure: null
});

export const SearchTypes = Types;
export default Creators;

export interface SearchState {
    fetching?: boolean,
    searchText?: string,
    recentlyViewed?: any[],
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

const searchAddRecent = (state: State, { item }: AnyAction) => {
    let newState;
    if (state.recentlyViewed && state.recentlyViewed.length > 0) {

        // Find index & filter out duplicates
        let index = state.recentlyViewed.findIndex(x => x.id === item.id);

        if (index !== -1) {
            newState = state.recentlyViewed.filter(obj => obj.id !== state.recentlyViewed[index].id);
            newState = { ...state, recentlyViewed: [item,...newState]};
        } else {
            newState = { ...state, recentlyViewed: [item, ...state.recentlyViewed]};
        }
    } else {
        newState = { ...state, recentlyViewed: [item]};
    }
    return state.merge(newState);
};

const searchRemoveRecent = (state: State, { item }: AnyAction) => {
    const result = state.recentlyViewed.filter(obj => obj.id !== item.id);
    return state.merge({ recentlyViewed: result });
};

const searchRemoveAllRecent = (state: State) => {
    const result = [];
    return state.merge({ recentlyViewed: result});
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
    searchAddRecent,
    searchRemoveRecent,
    searchRemoveAllRecent,
    searchSuccess,
    searchFailure
};

export const reducer = createReducer<State>(INITIAL_STATE, {
    [Types.SEARCH_REQUEST]: searchRequest,
    [Types.SEARCH_CLEAR]: searchClear,
    [Types.SEARCH_ADD_RECENT]: searchAddRecent,
    [Types.SEARCH_REMOVE_RECENT]: searchRemoveRecent,
    [Types.SEARCH_REMOVE_ALL_RECENT]: searchRemoveAllRecent,
    [Types.SEARCH_SUCCESS]: searchSuccess,
    [Types.SEARCH_FAILURE]: searchFailure
});