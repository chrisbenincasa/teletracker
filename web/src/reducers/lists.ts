import * as R from 'ramda';
import {
  ListActions,
  ListAddFailedAction,
  ListAddInitiatedAction,
  ListAddSuccessAction,
  ListRetrieveAllInitiatedAction,
  ListRetrieveAllSuccessAction,
  ListRetrieveInitiatedAction,
  ListRetrieveSuccessAction,
  LIST_RETRIEVE_ALL_INITIATED,
  USER_SELF_DELETE_LIST_SUCCESS,
  UserDeleteListSuccessAction,
} from '../actions/lists';
import {
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_ADD_ITEM_FAILED,
} from '../actions/lists/add_item_to_list';
import {
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
} from '../actions/lists/get_list';
import {
  // LIST_RETRIEVE_ALL_INITIATED,
  LIST_RETRIEVE_ALL_SUCCESS,
} from '../actions/lists/retrieve_all_lists';
import { USER_SELF_UPDATE_LIST_SUCCESS } from '../actions/lists/update_list';
import { List } from '../types';
import { flattenActions, handleAction } from './utils';
import Thing from '../types/Thing';
import { UserUpdateListSuccessAction } from '../actions/lists/update_list';

export type Loading = { [X in ListActions['type']]: boolean };

export interface ListOperationState {
  inProgress: boolean;
  operationType?: string;
}

export interface ListsByIdMap {
  [key: string]: List;
}

export interface State {
  operation: ListOperationState;
  listsById: ListsByIdMap;
  loading: Partial<Loading>;
  currentBookmark?: string;
}

const initialState: State = {
  operation: {
    inProgress: false,
  },
  listsById: {},
  loading: {},
};

const handleListAddInitiated = handleAction<ListAddInitiatedAction, State>(
  LIST_ADD_ITEM_INITIATED,
  state => {
    return {
      ...state,
      operation: {
        operationType: LIST_ADD_ITEM_INITIATED,
        inProgress: true,
      },
      loading: {
        ...state.loading,
        [LIST_ADD_ITEM_INITIATED]: true,
      },
    };
  },
);

function listAddItemFinished(state: State): State {
  return {
    ...state,
    operation: {
      ...state.operation,
      // operationType: undefined,
      inProgress: false,
    },
    loading: {
      ...state.loading,
      [LIST_ADD_ITEM_INITIATED]: false,
    },
  } as State;
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
  return {
    ...state,
    operation: {
      ...state.operation,
      operationType: LIST_RETRIEVE_INITIATED,
      inProgress: true,
    },
    loading: {
      ...state.loading,
      [LIST_RETRIEVE_INITIATED]: true,
    },
  };
});

const handleListRetrieveAllInitiated = handleAction<
  ListRetrieveAllInitiatedAction,
  State
>(LIST_RETRIEVE_ALL_INITIATED, state => {
  return {
    ...state,
    operation: {
      ...state.operation,
      operationType: LIST_RETRIEVE_ALL_INITIATED,
      inProgress: true,
    },
    loading: {
      ...state.loading,
      [LIST_RETRIEVE_ALL_INITIATED]: true,
    },
  };
});

const handleListDeleteSuccess = handleAction<
  UserDeleteListSuccessAction,
  State
>(USER_SELF_DELETE_LIST_SUCCESS, (state, { payload }) => {
  if (payload) {
    delete state.listsById[payload.listId];
  }
  return state;
});

const groupById = (things: Thing[]) =>
  R.groupBy(R.pipe(R.prop('id'), R.toString), things);

const headOption = R.ifElse(R.isNil, R.always(undefined), R.head);

const mergeThingLists = (key: string, left: any, right: any) => {
  if (key === 'things') {
    let leftList: Thing[] = left;
    let rightList: Thing[] = right;

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

  return {
    ...state,
    listsById: {
      ...state.listsById,
      [listId]: newList,
    },
    operation: {
      ...state.operation,
      operationType: undefined,
      inProgress: false,
    },
    loading: {
      ...state.loading,
      [LIST_RETRIEVE_INITIATED]: false,
    },
    currentBookmark: action.payload!!.paging
      ? action.payload!!.paging.bookmark
      : undefined,
  };
});

const handleUserRetrieve = handleAction<ListRetrieveAllSuccessAction, State>(
  LIST_RETRIEVE_ALL_SUCCESS,
  (state, action) => {
    let newListsById: ListsByIdMap = {};
    if (action.payload) {
      try {
        newListsById = R.reduce(
          (newListsById, list) => {
            return {
              ...newListsById,
              [list.id]: setOrMergeList(state.listsById[list.id], list, false),
            };
          },
          {} as ListsByIdMap,
          action.payload,
        );
      } catch (e) {
        console.error(e);
      }
    }

    return {
      ...state,
      listsById: {
        ...state.listsById,
        ...newListsById,
      },
      operation: {
        ...state.operation,
        operationType: undefined,
        inProgress: false,
      },
      loading: {
        ...state.loading,
        [LIST_RETRIEVE_ALL_INITIATED]: false,
      },
    };
  },
);

const handleListUpdate = handleAction<UserUpdateListSuccessAction, State>(
  USER_SELF_UPDATE_LIST_SUCCESS,
  (state, action) => {
    if (action.payload && state.listsById[action.payload.listId]) {
      let list = state.listsById[action.payload.listId];
      if (action.payload.name) {
        list.name = action.payload.name;
      }
    }

    return state;
  },
);

export default flattenActions<State>(
  'lists',
  initialState,
  handleListAddInitiated,
  handleListAddSuccess,
  handleListAddFailed,
  handleListRetrieveInitiated,
  handleListRetrieveSuccess,
  handleUserRetrieve,
  handleListRetrieveAllInitiated,
  handleListUpdate,
  handleListDeleteSuccess,
);
