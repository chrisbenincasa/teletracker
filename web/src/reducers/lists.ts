import {
  ListAddFailedAction,
  ListAddInitiatedAction,
  ListAddSuccessAction,
  ListRetrieveInitiatedAction,
  ListRetrieveSuccessAction,
} from '../actions/lists';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
} from '../constants/lists';
import { List } from '../types';
import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import { USER_SELF_RETRIEVE_SUCCESS } from '../constants/user';
import { UserSelfRetrieveSuccessAction } from '../actions/user';

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
}

const initialState: State = {
  operation: {
    inProgress: false,
  },
  listsById: {},
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
    };
  },
);

function listAddItemFinished(s: State): State {
  return {
    ...s,
    operation: {
      ...s.operation,
      // operationType: undefined,
      inProgress: false,
    },
  };
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
      operationType: LIST_ADD_ITEM_INITIATED,
      inProgress: true,
    },
  };
});

function setOrMergeList(
  listsById: ListsByIdMap,
  listId: number,
  newList: List | undefined,
) {
  let existing = listsById[listId];
  if (existing) {
    listsById[listId] = R.mergeDeepRight(existing, newList);
  } else if (newList) {
    listsById[listId] = newList;
  }
}

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  let listId = action.payload!!.id;
  let existing = state.listsById[listId];
  if (existing) {
    state.listsById[listId] = R.mergeDeepRight(existing, action.payload);
  } else {
    state.listsById[listId] = action.payload!;
  }

  state.listsById[action.payload!!.id] = action.payload!;

  return {
    ...state,
    operation: {
      ...state.operation,
      operationType: undefined,
      inProgress: false,
    },
  };
});

const handleUserRetrieve = handleAction<UserSelfRetrieveSuccessAction, State>(
  USER_SELF_RETRIEVE_SUCCESS,
  (state, action) => {
    if (action.payload && action.payload.lists) {
      action.payload.lists.forEach(list => {
        setOrMergeList(state.listsById, list.id, list);
      });
    }

    return state;
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
);
