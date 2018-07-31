import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    fetchMovie: ['id'],
    fetchShow: ['id']
});

export const ItemTypes = Types;
export default Creators;

export interface ItemState {
    actionInProgress?: boolean,
    error?: boolean
}

type State = Immutable.ImmutableObject<ItemState>

export const INITIAL_STATE = Immutable<ItemState>({});

export const reducers = {
    
};

export const reducer = createReducer<{}>(INITIAL_STATE, {
    
});