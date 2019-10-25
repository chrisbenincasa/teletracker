import * as R from 'ramda';
import {
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
} from '../actions/item-detail';
import {
  ListRetrieveSuccessAction,
  LIST_RETRIEVE_SUCCESS,
} from '../actions/lists';
import {
  PopularSuccessfulAction,
  POPULAR_SUCCESSFUL,
} from '../actions/popular';
import {
  UserRemoveItemTagsSuccessAction,
  UserUpdateItemTagsPayload,
  UserUpdateItemTagsSuccessAction,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
} from '../actions/user';
import { Item, ItemFactory, ItemTag } from '../types/v2/Item';
import { flattenActions, handleAction } from './utils';

export type ThingMap = {
  [key: string]: Item;
};

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: Item;
  thingsById: ThingMap;
  thingsBySlug: ThingMap;
}

const initialState: State = {
  fetching: false,
  thingsById: {},
  thingsBySlug: {},
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
      itemDetail: newThing,
      thingsById: {
        ...state.thingsById,
        [payload!.id]: newThing,
      } as ThingMap,
      thingsBySlug: {
        ...state.thingsBySlug,
        [payload!.slug]: newThing,
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
  let newThingsBySlug = newThingsMerged.reduce((prev, curr) => {
    return {
      ...prev,
      [curr.slug]: curr,
    };
  }, {});

  return {
    ...existingState,
    thingsById: {
      ...existingState.thingsById,
      ...newThingsById,
    },
    thingsBySlug: {
      ...existingState.thingsBySlug,
      ...newThingsBySlug,
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
    console.log(payload);

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
  initialState,
  itemFetchInitiated,
  itemFetchSuccess,
  itemUpdateTagsSuccess,
  itemRemoveTagsSuccess,
  handleListRetrieveSuccess,
  handlePopularRetrieveSuccess,
);
