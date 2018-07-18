import { AnyAction } from 'redux';
import { createActions, createReducer } from 'reduxsauce';
import Immutable from 'seamless-immutable';

const { Types, Creators } = createActions({
    retrieveEvents: null,
    retrieveEventsSuccess: ['response'],
    retrieveEventsFailure: null
});

export const EventTypes = Types;
export default Creators;

export interface EventsState {
    actionInProgress?: boolean,
    loadedEvents: any[]
    error?: boolean
}

type State = Immutable.ImmutableObject<EventsState>

export const INITIAL_STATE = Immutable<EventsState>({
    loadedEvents: []
});

const retrieveEvents = (state: State, action: AnyAction) => {
    return state.merge({ actionInProgress: true });
};

const retrieveEventsSuccess = (state: State, { response }: AnyAction) => {
    return state.merge({ actionInProgress: false, loadedEvents: response.data, error: null });
};

const retrieveEventsFailure = (state: State) => {
    return state.merge({ actionInProgress: false, error: true })
}

export const reducers = {
    retrieveEvents,
    retrieveEventsSuccess,
    retrieveEventsFailure
};

export const reducer = createReducer<{}>(INITIAL_STATE, {
    [Types.RETRIEVE_EVENTS]: retrieveEvents,
    [Types.RETRIEVE_EVENTS_SUCCESS]: retrieveEventsSuccess,
    [Types.RETRIEVE_EVENTS_FAILURE]: retrieveEventsFailure
});