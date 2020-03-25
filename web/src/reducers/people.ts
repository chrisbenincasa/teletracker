import { flattenActions, handleAction } from './utils';
import {
  PERSON_FETCH_INITIATED,
  PERSON_FETCH_SUCCESSFUL,
  PersonFetchInitiatedAction,
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
import { Id, Slug } from '../types/v2';
import {
  PERSON_CREDITS_FETCH_INITIATED,
  PERSON_CREDITS_FETCH_SUCCESSFUL,
  PersonCreditsFetchInitiatedAction,
  PersonCreditsFetchSuccessfulAction,
} from '../actions/people/get_credits';
import * as R from 'ramda';

interface PersonDetailState {
  current?: Id | Slug;
  credits?: string[]; // Array of popular slugs
  loading: boolean;
  bookmark?: string;
}

export interface State {
  loadingPeople: boolean;
  peopleById: { [key: string]: Person };
  nameByIdOrSlug: { [key: string]: string };
  detail?: PersonDetailState;
}

export const initialState: State = {
  loadingPeople: false,
  peopleById: {},
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
      return updateStateWithNewPeople(state, [payload.person]);
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
      return {
        ...state,
        ...updateStateWithNewPeople(state, payload),
        detail: {
          ...state.detail,
          loading: false,
        },
      };
    } else {
      return state;
    }
  },
);

const peopleCreditsFetchInitiated = handleAction(
  PERSON_CREDITS_FETCH_INITIATED,
  (state: State, { payload }: PersonCreditsFetchInitiatedAction) => {
    return {
      ...state,
      detail: {
        ...state.detail,
        current: payload?.personId,
        loading: true,
      },
    };
  },
);

const peopleCreditsFetchSuccess = handleAction(
  PERSON_CREDITS_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonCreditsFetchSuccessfulAction) => {
    if (payload) {
      let newCredits: string[];
      if (payload.append) {
        let existing = state.detail ? state.detail.credits || [] : [];
        newCredits = existing.concat(R.map(t => t.id, payload.credits));
      } else {
        newCredits = R.map(t => t.id, payload.credits);
      }

      return {
        ...state,
        detail: {
          ...state.detail,
          current: payload.personId,
          loading: false,
          credits: newCredits,
          bookmark: payload.paging ? payload.paging.bookmark : undefined,
        },
      };
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

const handlePersonFetchInitiated = handleAction<
  PersonFetchInitiatedAction,
  State
>(PERSON_FETCH_INITIATED, (state, action) => {
  if (action.payload) {
    let detail = state.detail;

    // Clear out previously loaded credits if we've navigated to a new page.
    if (action.payload.forDetailPage) {
      detail = {
        loading: true,
        current: action.payload.id,
      };
    }

    return {
      ...state,
      loadingPeople: true,
      detail,
    };
  } else {
    return state;
  }
});

const loadingPeople = [PEOPLE_FETCH_INITIATED, PEOPLE_SEARCH_INITIATED].map(
  actionType => {
    return handleAction<FSA<typeof actionType>, State>(actionType, state => {
      return {
        ...state,
        loadingPeople: true,
      };
    });
  },
);

export default flattenActions(
  'people',
  initialState,
  personFetchSuccess,
  personSearchSuccess,
  peopleFetchSuccess,
  handleListRetrieveSuccess,
  handlePersonFetchInitiated,
  ...loadingPeople,
  peopleCreditsFetchInitiated,
  peopleCreditsFetchSuccess,
);
