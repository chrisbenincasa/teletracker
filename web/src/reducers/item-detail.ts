import * as R from 'ramda';
import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
  ITEM_PREFETCH_SUCCESSFUL,
  ItemPrefetchSuccessfulAction,
} from '../actions/item-detail';
import {
  LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessAction,
} from '../actions/lists';
import {
  POPULAR_SUCCESSFUL,
  PopularSuccessfulAction,
} from '../actions/popular';
import {
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  UserRemoveItemTagsSuccessAction,
  UserUpdateItemTagsPayload,
  UserUpdateItemTagsSuccessAction,
} from '../actions/user';
import { Item, ItemFactory, ItemTag } from '../types/v2/Item';
import { flattenActions, handleAction } from './utils';
import {
  EXPLORE_SUCCESSFUL,
  ExploreSuccessfulAction,
} from '../actions/explore';
import { SEARCH_SUCCESSFUL, SearchSuccessfulAction } from '../actions/search';
import {
  PERSON_CREDITS_FETCH_SUCCESSFUL,
  PersonCreditsFetchSuccessfulAction,
} from '../actions/people/get_credits';

export type ThingMap = {
  [key: string]: Item;
};

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: string;
  thingsById: ThingMap;
}

const initialState: State = {
  fetching: false,
  thingsById: {},
};

const itemFetchInitiated = handleAction(
  ITEM_FETCH_INITIATED,
  (state: State, { payload }: ItemFetchInitiatedAction) => {
    return {
      ...state,
      fetching: true,
      currentId: payload,
      itemDetail: undefined,
    } as State;
  },
);

const itemPrefetchSuccess = handleAction(
  ITEM_PREFETCH_SUCCESSFUL,
  (state: State, { payload }: ItemPrefetchSuccessfulAction) => {
    let thingsById = state.thingsById || {};
    let existingThing: Item | undefined = thingsById[payload!.id];

    let newThing: Item = payload!;
    if (existingThing) {
      newThing = ItemFactory.merge(existingThing, newThing);
    }

    // TODO: Truncate thingsById after a certain point
    return {
      ...state,
      fetching: false,
      itemDetail: newThing.id,
      thingsById: {
        ...state.thingsById,
        [payload!.id]: newThing,
      } as ThingMap,
    } as State;
  },
);

const itemFetchSuccess = handleAction(
  ITEM_FETCH_SUCCESSFUL,
  (state: State, { payload }: ItemFetchSuccessfulAction) => {
    let thingsById = state.thingsById || {};
    let existingThing: Item | undefined = thingsById[payload!.id];

    let newThing: Item = ItemFactory.create(payload!);
    if (existingThing) {
      newThing = ItemFactory.merge(existingThing, newThing);
    }

    // TODO: Truncate thingsById after a certain point
    return {
      ...state,
      fetching: false,
      itemDetail: newThing.id,
      thingsById: {
        ...state.thingsById,
        [payload!.id]: newThing,
      } as ThingMap,
    } as State;
  },
);

const updateStateWithNewThings = (existingState: State, newThings: Item[]) => {
  let thingsById = existingState.thingsById || {};
  let newThingsMerged = newThings.map(curr => {
    let existingThing: Item | undefined = thingsById[curr.id];
    let newThing: Item = ItemFactory.create(curr);
    if (existingThing) {
      newThing = ItemFactory.merge(existingThing, newThing);
    }

    return newThing;
  });

  let newThingsById = newThingsMerged.reduce((prev, curr) => {
    return {
      ...prev,
      [curr.id]: curr,
    };
  }, {});

  return {
    ...existingState,
    fetching: false,
    thingsById: {
      ...existingState.thingsById,
      ...newThingsById,
    },
  };
};

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  if (action.payload && action.payload.list.items) {
    let items = action.payload.list.items;
    return updateStateWithNewThings(state, items);
  } else {
    return state;
  }
});

const handlePopularRetrieveSuccess = handleAction<
  PopularSuccessfulAction,
  State
>(POPULAR_SUCCESSFUL, (state, action) => {
  if (action.payload && action.payload.popular) {
    let things = action.payload.popular;
    return updateStateWithNewThings(state, things);
  } else {
    return state;
  }
});

const handleExploreRetrieveSuccess = handleAction<
  ExploreSuccessfulAction,
  State
>(EXPLORE_SUCCESSFUL, (state, action) => {
  if (action.payload && action.payload.items) {
    let things = action.payload.items;
    return updateStateWithNewThings(state, things);
  } else {
    return state;
  }
});

const peopleCreditsFetchSuccess = handleAction(
  PERSON_CREDITS_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonCreditsFetchSuccessfulAction) => {
    if (payload && payload.credits) {
      let things = payload.credits;
      return updateStateWithNewThings(state, things);
    } else {
      return state;
    }
  },
);

const handleSearchRetrieveSuccess = handleAction<SearchSuccessfulAction, State>(
  SEARCH_SUCCESSFUL,
  (state, action) => {
    if (action.payload && action.payload.results) {
      let things = action.payload.results;
      return updateStateWithNewThings(state, things);
    } else {
      return state;
    }
  },
);

const filterNot = <T>(fn: (x: T) => boolean, arr: T[]) => {
  return R.filter(R.complement(fn), arr);
};

// Updates the current item's tags
const updateTagsState = (
  state: State,
  fn: (tags: ItemTag[]) => ItemTag[],
  payload?: UserUpdateItemTagsPayload,
) => {
  let thingsById = state.thingsById || {};
  let thingId = payload!.thingId;
  if (payload && thingsById[thingId] && thingsById[thingId].tags) {
    let thing = thingsById[thingId]!;
    let newTagSet = fn(thing.tags!);

    return {
      ...state,
      thingsById: {
        ...thingsById,
        [thingId]: {
          ...thing,
          tags: newTagSet,
        },
      },
    } as State;
  } else {
    return state;
  }
};

const itemUpdateTagsSuccess = handleAction(
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  (state: State, { payload }: UserUpdateItemTagsSuccessAction) => {
    return updateTagsState(
      state,
      tags => {
        return R.append(
          { tag: payload!.action, value: payload!.value },
          filterNot(R.propEq('tag', payload!.action), tags),
        );
      },
      payload,
    );
  },
);

const itemRemoveTagsSuccess = handleAction(
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  (state: State, { payload }: UserRemoveItemTagsSuccessAction) => {
    return updateTagsState(
      state,
      tags => {
        return filterNot(R.propEq('action', payload!.action), tags);
      },
      payload,
    );
  },
);

export default flattenActions(
  'item-detail',
  initialState,
  itemFetchInitiated,
  itemFetchSuccess,
  itemUpdateTagsSuccess,
  itemRemoveTagsSuccess,
  handleListRetrieveSuccess,
  handlePopularRetrieveSuccess,
  handleExploreRetrieveSuccess,
  handleSearchRetrieveSuccess,
  peopleCreditsFetchSuccess,
  itemPrefetchSuccess,
);
