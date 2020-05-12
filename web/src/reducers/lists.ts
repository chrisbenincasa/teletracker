import * as R from 'ramda';
import {
  LIST_RETRIEVE_ALL_INITIATED,
  ListActions,
  ListAddFailedAction,
  ListAddInitiatedAction,
  ListAddSuccessAction,
  ListRetrieveAllInitiatedAction,
  ListRetrieveAllSuccessAction,
  ListRetrieveInitiatedAction,
  ListRetrieveSuccessAction,
  USER_SELF_DELETE_LIST_SUCCESS,
  UserDeleteListSuccessAction,
} from '../actions/lists';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
} from '../actions/lists/add_item_to_list';
import {
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
} from '../actions/lists/get_list';
import { LIST_RETRIEVE_ALL_SUCCESS } from '../actions/lists/retrieve_all_lists';
import {
  USER_SELF_UPDATE_LIST_SUCCESS,
  UserUpdateListSuccessAction,
} from '../actions/lists/update_list';
import { ActionType, List } from '../types';
import { flattenActions, handleAction } from './utils';
import {
  USER_SELF_REMOVE_ITEM_TAGS,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  UserRemoveItemTagsAction,
  UserRemoveItemTagsSuccessAction,
  UserUpdateItemTagsSuccessAction,
} from '../actions/user';
import { FilterParams } from '../utils/searchFilters';
import { Item } from '../types/v2/Item';
import { Map, Record, RecordOf } from 'immutable';

export type LoadingType = { [X in ListActions['type']]?: boolean };
export type Loading = RecordOf<LoadingType>;
const makeLoading = Record<LoadingType>({});

export type ListOperationState = {
  inProgress: boolean;
  operationType?: string;
};
const makeListOperationState = Record<ListOperationState>({
  inProgress: false,
});

export type ListsByIdMap = Map<string, List>;

type StateType = {
  operation: RecordOf<ListOperationState>;
  listsById: ListsByIdMap;
  loading: Loading;
  currentBookmark?: string;
  currentFilters?: FilterParams;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {
  operation: makeListOperationState(),
  listsById: Map({}),
  loading: makeLoading(),
};

export const makeState = Record(initialState);

const handleListAddInitiated = handleAction<ListAddInitiatedAction, State>(
  LIST_ADD_ITEM_INITIATED,
  state => {
    return state.merge({
      operation: state.operation.merge({
        inProgress: true,
        operationType: LIST_ADD_ITEM_INITIATED,
      }),
      loading: state.loading.set(LIST_ADD_ITEM_INITIATED, true),
    });
  },
);

function listAddItemFinished(state: State): State {
  return state.merge({
    operation: state.operation.set('inProgress', false),
    loading: state.loading.set(LIST_ADD_ITEM_INITIATED, false),
  });
}

const handleListAddSuccess = handleAction<ListAddSuccessAction, State>(
  LIST_ADD_ITEM_SUCCESS,
  (state: State) => listAddItemFinished(state),
);

const handleListAddFailed = handleAction<ListAddFailedAction, State>(
  LIST_ADD_ITEM_FAILED,
  (state: State) => listAddItemFinished(state),
);

const handleListRetrieveInitiated = handleAction<
  ListRetrieveInitiatedAction,
  State
>(LIST_RETRIEVE_INITIATED, state => {
  return state.merge({
    operation: state.operation.merge({
      operationType: LIST_RETRIEVE_INITIATED,
      inProgress: true,
    }),
    loading: state.loading.set(LIST_RETRIEVE_INITIATED, true),
  });
});

const handleListRetrieveAllInitiated = handleAction<
  ListRetrieveAllInitiatedAction,
  State
>(LIST_RETRIEVE_ALL_INITIATED, state => {
  return state.merge({
    operation: state.operation.merge({
      operationType: LIST_RETRIEVE_ALL_INITIATED,
      inProgress: true,
    }),
    loading: state.loading.set(LIST_RETRIEVE_ALL_INITIATED, true),
  });
});

const handleListDeleteSuccess = handleAction<
  UserDeleteListSuccessAction,
  State
>(USER_SELF_DELETE_LIST_SUCCESS, (state, { payload }) => {
  if (payload) {
    return state.set('listsById', state.listsById.remove(payload.listId));
  }
  return state;
});

const groupById = (things: Item[]) =>
  R.groupBy(R.pipe(R.prop('id'), R.toString), things);

const headOption = R.ifElse(R.isNil, R.always(undefined), R.head);

const mergeThingLists = (key: string, left: any, right: any) => {
  if (key === 'things') {
    let leftList: Item[] = left;
    let rightList: Item[] = right;

    let leftThingsById = leftList ? groupById(leftList) : {};

    if (rightList) {
      return rightList.map(thing => {
        let leftThing = headOption(leftThingsById[thing.id]);

        if (leftThing) {
          // Perform the merge.
          return R.mergeDeepRight(leftThing, thing);
        } else {
          return thing;
        }
      });
    } else {
      return [];
    }
  } else {
    return right;
  }
};

function setOrMergeList(
  existing: List | undefined,
  newList: List | undefined,
  append: boolean,
) {
  if (existing) {
    if (!append) {
      return R.mergeDeepWithKey(mergeThingLists, existing, newList);
    } else {
      return {
        ...existing,
        items: R.concat(existing.items || [], newList?.items || []),
      };
    }
  } else if (newList) {
    return newList;
  }
}

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  let listId = action.payload!!.list.id;

  let newList = setOrMergeList(
    state.listsById[listId],
    action.payload!!.list,
    action.payload!!.append,
  );

  return state.merge({
    listsById: state.listsById.set(listId, newList),
    operation: makeListOperationState({ inProgress: false }),
    loading: state.loading.set(LIST_RETRIEVE_INITIATED, false),
    currentBookmark: action.payload!!.paging
      ? action.payload!!.paging.bookmark
      : undefined,
    currentFilters: action.payload!.forFilters,
  });
});

const handleUserRetrieve = handleAction<ListRetrieveAllSuccessAction, State>(
  LIST_RETRIEVE_ALL_SUCCESS,
  (state, action) => {
    let newListsById: ListsByIdMap = state.listsById;
    if (action.payload) {
      newListsById = action.payload.reduce((prev, curr) => {
        return prev.set(
          curr.id,
          setOrMergeList(prev.get(curr.id), curr, false),
        );
      }, state.listsById);
    }

    return state.merge({
      listsById: newListsById,
      operation: state.operation.merge({
        operationType: undefined,
        inProgress: false,
      }),
      loading: state.loading.set(LIST_RETRIEVE_ALL_INITIATED, false),
    });
  },
);

const handleListUpdate = handleAction<UserUpdateListSuccessAction, State>(
  USER_SELF_UPDATE_LIST_SUCCESS,
  (state, action) => {
    if (action.payload && state.listsById.get(action.payload.listId)) {
      let list = state.listsById.get(action.payload.listId);
      if (action.payload.name && list) {
        return state.set(
          'listsById',
          state.listsById.set(list.id, { ...list, name: action.payload.name }),
        );
      } else {
        return state;
      }
    } else {
      return state;
    }
  },
);

const handleUserUpdateTrackingSuccess = handleAction<
  UserUpdateItemTagsSuccessAction,
  State
>(USER_SELF_UPDATE_ITEM_TAGS_SUCCESS, (state, { payload }) => {
  if (payload?.action === ActionType.TrackedInList && payload?.string_value) {
    const existingList = state.listsById[payload.string_value];
    let newList = existingList;
    if (existingList) {
      newList = {
        ...existingList,
        totalItems: existingList.totalItems + 1,
      };
    }

    return state.set(
      'listsById',
      state.listsById.set(existingList.id, newList),
    );
  }

  return state;
});

const handleUserRemoveTrackingSuccess = handleAction<
  UserRemoveItemTagsSuccessAction,
  State
>(USER_SELF_REMOVE_ITEM_TAGS_SUCCESS, (state, { payload }) => {
  if (payload?.action === ActionType.TrackedInList && payload?.string_value) {
    const existingList = state.listsById[payload.string_value];
    let newList = existingList;
    if (existingList) {
      newList = {
        ...existingList,
        totalItems: existingList.totalItems - 1,
      };
    }

    return state.set(
      'listsById',
      state.listsById.set(existingList.id, newList),
    );
  }

  return state;
});

export default {
  initialState: makeState(),
  reducer: flattenActions<State>(
    'lists',
    makeState(),
    handleListAddInitiated,
    handleListAddSuccess,
    handleListAddFailed,
    handleListRetrieveInitiated,
    handleListRetrieveSuccess,
    handleUserRetrieve,
    handleListRetrieveAllInitiated,
    handleListUpdate,
    handleListDeleteSuccess,
    handleUserRemoveTrackingSuccess,
    handleUserUpdateTrackingSuccess,
  ),
};
