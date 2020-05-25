import * as R from 'ramda';
import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
  ITEM_PREFETCH_SUCCESSFUL,
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
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
import {
  PERSON_FETCH_SUCCESSFUL,
  PersonFetchSuccessfulAction,
} from '../actions/people/get_person';
import _ from 'lodash';
import {
  ITEM_RECOMMENDATIONS_FETCH_INITIATED,
  ITEM_RECOMMENDATIONS_FETCH_SUCCESSFUL,
  ItemRecsFetchInitiatedAction,
  ItemRecsFetchSuccessfulAction,
} from '../actions/item-detail/get_item_recommendations';
import {
  LIST_ITEMS_RETRIEVE_SUCCESS,
  ListItemsRetrieveSuccessAction,
} from '../actions/lists/get_list_items';

export type ThingMap = {
  [key: string]: Item;
};

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: string;
  // True if we tried to lookup recs. If false and we load an item, try to load
  // recommendations lazily.
  fetchingRecs: boolean;
  currentItemFetchedRecommendations?: boolean;
  thingsById: ThingMap;
}

const initialState: State = {
  fetching: false,
  fetchingRecs: false,
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
    if (payload) {
      let { item, includedRecommendations } = payload;
      let thingsById = state.thingsById || {};
      let existingThing: Item | undefined = thingsById[item.id];

      let newThing: Item = ItemFactory.create(item);
      if (existingThing) {
        newThing = ItemFactory.merge(existingThing, newThing);
      }

      let recommendations = (item.recommendations || []).map(
        ItemFactory.create,
      );

      // TODO: Truncate thingsById after a certain point
      return updateStateWithNewThings(
        {
          fetching: false,
          itemDetail: newThing.id,
          thingsById: {
            ...state.thingsById,
            [item.id]: newThing,
          } as ThingMap,
          currentItemFetchedRecommendations: includedRecommendations,
        } as State,
        recommendations,
      );
    } else {
      return state;
    }
  },
);

const itemRecommendationsFetch = handleAction(
  ITEM_RECOMMENDATIONS_FETCH_INITIATED,
  (state: State, { payload }: ItemRecsFetchInitiatedAction) => {
    if (payload) {
      return {
        ...state,
        fetchingRecs: true,
        currentItemFetchedRecommendations: true,
      };
    } else {
      return state;
    }
  },
);

const itemRecommendationsFetchSuccess = handleAction(
  ITEM_RECOMMENDATIONS_FETCH_SUCCESSFUL,
  (state: State, { payload }: ItemRecsFetchSuccessfulAction) => {
    if (payload) {
      let recommendations = (payload.items || []).map(ItemFactory.create);

      let forItem = state.thingsById[payload.forItem];

      let nextState = state;
      if (forItem) {
        forItem = {
          ...forItem,
          recommendations: recommendations.map(r => r.id),
        };
        nextState = {
          ...state,
          thingsById: {
            ...state.thingsById,
            [payload.forItem]: forItem,
          },
        };
      }

      return updateStateWithNewThings(nextState, recommendations);
    } else {
      return state;
    }
  },
);

const updateStateWithNewThings = (existingState: State, newThings: Item[]) => {
  if (newThings.length === 0) {
    return existingState;
  }

  let thingsById = existingState.thingsById || {};
  let newThingsMerged = newThings.map(curr => {
    let existingThing: Item | undefined = thingsById[curr.id];
    let newThing = curr;
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
  ListItemsRetrieveSuccessAction,
  State
>(LIST_ITEMS_RETRIEVE_SUCCESS, (state, action) => {
  if (action.payload && action.payload.items) {
    let items = action.payload.items;
    return updateStateWithNewThings(state, [...items]);
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

const personFetchSuccess = handleAction(
  PERSON_FETCH_SUCCESSFUL,
  (state: State, { payload }: PersonFetchSuccessfulAction) => {
    if ((payload?.rawPerson.cast_credits?.data?.length || 0) > 0) {
      let things = _.flatMap(
        payload!.rawPerson.cast_credits!.data || [],
        credit => (credit.item ? [credit.item!] : []),
      );
      return updateStateWithNewThings(state, things.map(ItemFactory.create));
    } else {
      return state;
    }
  },
);

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
  let itemId = payload!.itemId;
  if (payload && thingsById[itemId]) {
    let thing = thingsById[itemId]!;
    let newTagSet = fn(thing.tags || []);

    return {
      ...state,
      thingsById: {
        ...thingsById,
        [itemId]: {
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
    try {
      return updateTagsState(
        state,
        tags => {
          return R.append(
            {
              tag: payload!.action,
              value: payload!.value,
              string_value: payload!.string_value,
            },
            payload?.unique
              ? filterNot(
                  R.both(
                    R.propEq('tag', payload!.action),
                    R.propEq('string_value', payload!.string_value),
                  ),
                  tags,
                )
              : filterNot(R.propEq('tag', payload!.action), tags),
          );
        },
        payload,
      );
    } catch (e) {
      console.error(e);
      throw e;
    }
  },
);

const itemRemoveTagsSuccess = handleAction(
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  (state: State, { payload }: UserRemoveItemTagsSuccessAction) => {
    return updateTagsState(
      state,
      tags => {
        return payload?.unique
          ? filterNot(
              R.both(
                R.propEq('tag', payload!.action),
                R.propEq('string_value', payload!.string_value),
              ),
              tags,
            )
          : filterNot(R.propEq('tag', payload!.action), tags);
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
  personFetchSuccess,
  itemRecommendationsFetch,
  itemRecommendationsFetchSuccess,
);
