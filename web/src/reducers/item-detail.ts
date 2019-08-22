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
import { Thing, UserThingTag } from '../types';
import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import {
  LIST_RETRIEVE_SUCCESS,
  ListRetrieveSuccessAction,
} from '../actions/lists';

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: Thing;
  thingsById: { [key: number]: Thing };
  thingsBySlug: { [key: string]: Thing };
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
    } as State;
  },
);

const itemFetchSuccess = handleAction(
  ITEM_FETCH_SUCCESSFUL,
  (state: State, { payload }: ItemFetchSuccessfulAction) => {
    let thingsById = state.thingsById || {};
    let existingThing: Thing | undefined = thingsById[payload!.id];
    let newThing: Thing = payload!;
    if (existingThing) {
      newThing = R.mergeDeepRight(existingThing, newThing) as Thing;
    }

    // TODO: Truncate thingsById after a certain point
    return {
      ...state,
      fetching: false,
      itemDetail: payload!,
      thingsById: {
        ...state.thingsById,
        [payload!.id]: newThing,
      },
      thingsBySlug: {
        ...state.thingsBySlug,
        [payload!.normalizedName]: newThing,
      },
    } as State;
  },
);

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  if (action.payload && action.payload.things) {
    let thingsById = state.thingsById || {};
    let things = action.payload.things;
    let newThings = things.map(curr => {
      let existingThing: Thing | undefined = thingsById[curr.id];
      let newThing: Thing = curr;
      if (existingThing) {
        newThing = R.mergeDeepRight(existingThing, newThing) as Thing;
      }

      return newThing;
    });

    let newThingsById = newThings.reduce((prev, curr) => {
      return {
        ...prev,
        [curr.id]: curr,
      };
    }, {});
    let newThingsBySlug = newThings.reduce((prev, curr) => {
      return {
        ...prev,
        [curr.normalizedName]: curr,
      };
    }, {});

    return {
      ...state,
      thingsById: {
        ...state.thingsById,
        ...newThingsById,
      },
      thingsBySlug: {
        ...state.thingsBySlug,
        ...newThingsBySlug,
      },
    };
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
);
