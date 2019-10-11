import * as R from 'ramda';
import {
  ListAddFailedAction,
  ListAddInitiatedAction,
  ListAddSuccessAction,
  ListRetrieveAllInitiatedAction,
  ListRetrieveAllSuccessAction,
  ListRetrieveInitiatedAction,
  ListRetrieveSuccessAction,
  ListActions,
  LIST_ADD_ITEM_INITIATED,
  LIST_RETRIEVE_ALL_INITIATED,
  LIST_RETRIEVE_INITIATED,
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_SUCCESS,
  LIST_RETRIEVE_ALL_SUCCESS,
} from '../actions/lists';
import { List } from '../types';
import { flattenActions, handleAction } from './utils';
import Thing from '../types/Thing';

export type Loading = { [X in ListActions['type']]: boolean };

export interface ListOperationState {
  inProgress: boolean;
  operationType?: string;
}

export interface ListsByIdMap {
  [key: number]: List;
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

const groupById = (things: Thing[]) =>
  R.groupBy(
    R.pipe(
      R.prop('id'),
      R.toString,
    ),
    things,
  );

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
      existing.things = R.concat(
        existing.things,
        newList ? newList.things : [],
      );
      return existing;
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

        Object.keys(state.listsById).forEach(key => {
          if (!newListsById.hasOwnProperty(key)) {
            delete state.listsById[key];
          }
        });
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

export default flattenActions<State>(
  initialState,
  handleListAddInitiated,
  handleListAddSuccess,
  handleListAddFailed,
  handleListRetrieveInitiated,
  handleListRetrieveSuccess,
  handleUserRetrieve,
  handleListRetrieveAllInitiated,
);
