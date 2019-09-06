import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
} from '../actions/item-detail';
import {
  UserUpdateItemTagsSuccessAction,
  UserRemoveItemTagsSuccessAction,
  UserUpdateItemTagsPayload,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
} from '../actions/user';
import { UserThingTag } from '../types';
import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import {
  LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessAction,
} from '../actions/lists';
import {
  POPULAR_SUCCESSFUL,
  PopularSuccessfulAction,
} from '../actions/popular';
import Thing, { ApiThing, ThingFactory } from '../types/Thing';

export type ThingMap = {
  [key: string]: Thing;
};

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: Thing;
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
    let existingThing: Thing | undefined = thingsById[payload!.id];

    let newThing: Thing = ThingFactory.create(payload!);
    if (existingThing) {
      newThing = ThingFactory.merge(existingThing, newThing);
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
        [payload!.normalizedName]: newThing,
      } as ThingMap,
    } as State;
  },
);

const updateStateWithNewThings = (
  existingState: State,
  newThings: ApiThing[],
) => {
  let thingsById = existingState.thingsById || {};
  let newThingsMerged = newThings.map(curr => {
    let existingThing: Thing | undefined = thingsById[curr.id];
    let newThing: Thing = ThingFactory.create(curr);
    if (existingThing) {
      newThing = ThingFactory.merge(existingThing, newThing);
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
  if (action.payload && action.payload.things) {
    let things = action.payload.things;
    return updateStateWithNewThings(state, things);
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
  fn: (tags: UserThingTag[]) => UserThingTag[],
  payload?: UserUpdateItemTagsPayload,
) => {
  let thingsById = state.thingsById || {};
  let thingId = payload!.thingId;
  if (payload && thingsById[thingId] && thingsById[thingId].userMetadata) {
    let thing = thingsById[thingId]!;
    let newTagSet = fn(thing.userMetadata!.tags);

    return {
      ...state,
      thingsById: {
        ...thingsById,
        [thingId]: {
          ...thing,
          userMetadata: {
            ...thing.userMetadata,
            tags: newTagSet,
          },
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
          { action: payload!.action, value: payload!.value },
          filterNot(R.propEq('action', payload!.action), tags),
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
