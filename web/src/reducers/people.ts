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
import { FilterParams } from '../utils/searchFilters';
import { List, RecordOf, Map, Record } from 'immutable';

type PersonDetailState = {
  current?: Id | Slug;
  credits?: List<string>; // Array of popular slugs
  loading: boolean;
  bookmark?: string;
  currentFilters?: FilterParams;
};
const makePersonDetailState = Record<PersonDetailState>({
  loading: false,
});

export type StateType = {
  loadingPeople: boolean;
  peopleById: Map<Id, Person>;
  nameByIdOrSlug: Map<Id | Slug, string>;
  detail?: RecordOf<PersonDetailState>;
};

export type State = RecordOf<StateType>;

export const initialState: StateType = {
  loadingPeople: false,
  peopleById: Map({}),
  nameByIdOrSlug: Map({}),
};

export const makeState = Record(initialState);

const updateStateWithNewPeople = (state: State, newPeople: Person[]) => {
  const newThingsMerged = newPeople.reduce((acc, person) => {
    const existingPerson = state.peopleById.get(person.id);
    let newThing = person;
    if (existingPerson) {
      newThing = PersonFactory.merge(existingPerson, newThing);
    }

    return acc.set(newThing.id, newThing);
  }, Map<Id, Person>());

  const newPeopleNames = newThingsMerged.mapEntries(([_, person]) => {
    return [(person.slug as Slug) || (person.id as Id), person.name];
  });

  // TODO: Truncate thingsById after a certain point
  return state.merge({
    loadingPeople: false,
    peopleById: state.peopleById.merge(newThingsMerged),
    nameByIdOrSlug: state.nameByIdOrSlug.merge(newPeopleNames),
  });
};

const personFetchSuccess = handleAction(
  PERSON_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonFetchSuccessfulAction) => {
    if (payload) {
      return updateStateWithNewPeople(state, [payload.person]).set(
        'detail',
        (state.detail || makePersonDetailState()).set('loading', false),
      );
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
      return updateStateWithNewPeople(state, payload).set(
        'detail',
        (state.detail || makePersonDetailState()).set('loading', false),
      );
    } else {
      return state;
    }
  },
);

const peopleCreditsFetchInitiated = handleAction(
  PERSON_CREDITS_FETCH_INITIATED,
  (state: State, { payload }: PersonCreditsFetchInitiatedAction) => {
    return state.set(
      'detail',
      (state.detail || makePersonDetailState()).merge({
        current: payload?.personId,
        loading: true,
      }),
    );
  },
);

const peopleCreditsFetchSuccess = handleAction(
  PERSON_CREDITS_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonCreditsFetchSuccessfulAction) => {
    if (payload) {
      let newCredits: List<string>;
      if (payload.append) {
        let existing = state.detail?.credits || List();
        newCredits = existing.concat(R.map(t => t.id, payload.credits));
      } else {
        newCredits = List(payload.credits).map(c => c.id);
      }

      return state.set(
        'detail',
        (state.detail || makePersonDetailState()).merge({
          current: payload.personId,
          loading: false,
          credits: newCredits,
          bookmark: payload.paging ? payload.paging.bookmark : undefined,
          currentFilters: payload.forFilters,
        }),
      );
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
      detail = makePersonDetailState({
        loading: true,
        current: action.payload.id,
      });
    }

    return state.merge({
      loadingPeople: true,
      detail,
    });
  } else {
    return state;
  }
});

const loadingPeople = [PEOPLE_FETCH_INITIATED, PEOPLE_SEARCH_INITIATED].map(
  actionType => {
    return handleAction<FSA<typeof actionType>, State>(actionType, state => {
      return state.set('loadingPeople', true);
    });
  },
);

export default {
  initialState: makeState(),
  reducer: flattenActions(
    'people',
    makeState(),
    personFetchSuccess,
    personSearchSuccess,
    peopleFetchSuccess,
    handleListRetrieveSuccess,
    handlePersonFetchInitiated,
    ...loadingPeople,
    peopleCreditsFetchInitiated,
    peopleCreditsFetchSuccess,
  ),
};
