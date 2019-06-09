import {
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
} from '../actions/item-detail';
import {
  UserUpdateItemTagsSuccessAction,
  UserRemoveItemTagsSuccessAction,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
} from '../constants/item-detail';
import {
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
} from '../constants/user';
import { Thing, UserThingTag } from '../types';
import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';

export interface State {
  fetching: boolean;
  currentId?: number;
  itemDetail?: Thing;
}

const initialState: State = {
  fetching: false,
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
    return {
      ...state,
      fetching: false,
      itemDetail: payload!,
    } as State;
  },
);

const filterNot = <T>(fn: (x: T) => boolean, arr: T[]) => {
  return R.filter(R.complement(fn), arr);
};

// Updates the current item's tags
const updateTagsState = (
  state: State,
  fn: (tags: UserThingTag[]) => UserThingTag[],
  payload?: UserUpdateItemTagsPayload,
) => {
  if (
    payload &&
    state.currentId === payload!.thingId &&
    state.itemDetail &&
    state.itemDetail.userMetadata
  ) {
    let newTagSet = fn(state.itemDetail.userMetadata.tags);

    return {
      ...state,
      itemDetail: {
        ...state.itemDetail,
        userMetadata: {
          ...state.itemDetail.userMetadata,
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
);
