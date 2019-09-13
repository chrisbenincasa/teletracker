import { flattenActions, handleAction } from './utils';
import {
  PERSON_FETCH_SUCCESSFUL,
  PersonFetchSuccessfulAction,
} from '../actions/people/get_person';
import { Person, PersonFactory } from '../types/v2/Person';

export interface State {
  peopleById: { [key: string]: Person };
  peopleBySlug: { [key: string]: Person };
}

const initialState: State = {
  peopleById: {},
  peopleBySlug: {},
};

const personFetchSuccess = handleAction(
  PERSON_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonFetchSuccessfulAction) => {
    let peopleById = state.peopleById || {};
    let existingPerson: Person | undefined = peopleById[payload!.id];
    let newThing: Person = payload!;
    if (existingPerson) {
      newThing = PersonFactory.merge(existingPerson, newThing);
    }

    // TODO: Truncate thingsById after a certain point
    return {
      ...state,
      peopleById: {
        ...state.peopleById,
        [payload!.id]: newThing,
      },
      peopleBySlug: {
        ...state.peopleBySlug,
        [payload!.slug!]: newThing,
      },
    } as State;
  },
);

export default flattenActions(initialState, personFetchSuccess);
