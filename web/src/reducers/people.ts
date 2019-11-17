import { flattenActions, handleAction } from './utils';
import {
  PERSON_FETCH_INITIATED,
  PERSON_FETCH_SUCCESSFUL,
  PersonFetchSuccessfulAction,
} from '../actions/people/get_person';
import { Person, PersonFactory } from '../types/v2/Person';
import {
  PEOPLE_SEARCH_INITIATED,
  PEOPLE_SEARCH_SUCCESSFUL,
  PeopleSearchSuccessfulAction,
} from '../actions/search/person_search';
import {
  PEOPLE_FETCH_INITIATED,
  PEOPLE_FETCH_SUCCESSFUL,
  PeopleFetchSuccessfulAction,
} from '../actions/people/get_people';
import { FSA } from 'flux-standard-action';
import {
  LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessAction,
} from '../actions/lists';

export interface State {
  loadingPeople: boolean;
  peopleById: { [key: string]: Person };
  peopleBySlug: { [key: string]: Person };
  nameByIdOrSlug: { [key: string]: string };
}

export const initialState: State = {
  loadingPeople: false,
  peopleById: {},
  peopleBySlug: {},
  nameByIdOrSlug: {},
};

const updateStateWithNewPeople = (state: State, newPeople: Person[]) => {
  let peopleById = state.peopleById || {};
  let newThingsMerged = newPeople.map(person => {
    let existingPerson: Person | undefined = peopleById[person.id];
    let newThing: Person = person;
    if (existingPerson) {
      newThing = PersonFactory.merge(existingPerson, newThing);
    }

    return newThing;
  });

  let newThingsById = newThingsMerged.reduce((prev, curr) => {
    return {
      ...prev,
      [curr.id]: curr,
    };
  }, {});

  let newThingsBySlug = newThingsMerged.reduce((prev, curr) => {
    if (curr.slug) {
      return {
        ...prev,
        [curr.slug]: curr,
      };
    } else {
      return prev;
    }
  }, {});

  let newNamesBySlugOrId = newThingsMerged.reduce((prev, curr) => {
    return {
      ...prev,
      [curr.slug || curr.id]: curr.name,
    };
  }, {});

  // TODO: Truncate thingsById after a certain point
  return {
    ...state,
    loadingPeople: false,
    peopleById: {
      ...state.peopleById,
      ...newThingsById,
    },
    peopleBySlug: {
      ...state.peopleBySlug,
      ...newThingsBySlug,
    },
    nameByIdOrSlug: {
      ...state.nameByIdOrSlug,
      ...newNamesBySlugOrId,
    },
  };
};

const personFetchSuccess = handleAction(
  PERSON_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonFetchSuccessfulAction) => {
    if (payload) {
      return updateStateWithNewPeople(state, [payload]);
    } else {
      return state;
    }
  },
);

const personSearchSuccess = handleAction(
  PEOPLE_SEARCH_SUCCESSFUL,
  (state: State, { payload }: PeopleSearchSuccessfulAction) => {
    if (payload) {
      return updateStateWithNewPeople(state, payload.results);
    } else {
      return state;
    }
  },
);

const peopleFetchSuccess = handleAction(
  PEOPLE_FETCH_SUCCESSFUL,
  (state: State, { payload }: PeopleFetchSuccessfulAction) => {
    if (payload) {
      return updateStateWithNewPeople(state, payload);
    } else {
      return state;
    }
  },
);

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  if (action.payload && action.payload.list.relevantPeople) {
    let items = action.payload.list.relevantPeople;
    return updateStateWithNewPeople(state, items);
  } else {
    return state;
  }
});

const loadingPeople = [
  PERSON_FETCH_INITIATED,
  PEOPLE_FETCH_INITIATED,
  PEOPLE_SEARCH_INITIATED,
].map(actionType => {
  return handleAction<FSA<typeof actionType>, State>(actionType, state => {
    return {
      ...state,
      loadingPeople: true,
    };
  });
});

export default flattenActions(
  initialState,
  personFetchSuccess,
  personSearchSuccess,
  peopleFetchSuccess,
  handleListRetrieveSuccess,
  ...loadingPeople,
);
