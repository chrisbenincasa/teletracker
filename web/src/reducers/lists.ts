import {
  ListAddFailedAction,
  ListAddInitiatedAction,
  ListAddSuccessAction,
  ListRetrieveInitiatedAction,
  ListRetrieveSuccessAction,
  ListRetrieveAllSuccessAction,
  ListRetrieveAllInitiatedAction,
} from '../actions/lists';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
  LIST_RETRIEVE_ALL_SUCCESS,
  LIST_RETRIEVE_ALL_INITIATED,
} from '../constants/lists';
import { List } from '../types';
import { flattenActions, handleAction } from './utils';
import * as R from 'ramda';
import { USER_SELF_RETRIEVE_SUCCESS } from '../constants/user';
import { UserSelfRetrieveSuccessAction } from '../actions/user';
import { Thing } from '../types/external/themoviedb/Movie';

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

const mergeThingLists = (key: string, left: any, right: any) => {
  if (key === 'things') {
    // if (!(left as List).things && !(right as List).things) {
    //   debugger;
    //   console.log(left, right);

    //   return right;
    // }

    let leftList: Thing[] = left;
    let rightList: Thing[] = right;

    let leftThingsById = groupById(leftList);
    let rightThingsById = groupById(rightList);

    let res = R.union(R.keys(leftThingsById), R.keys(rightThingsById)).map(
      id => {
        let leftThing = R.head(leftThingsById[id.toString()]);
        let rightThing = R.head(rightThingsById[id.toString()]);

        if (leftThing && rightThing) {
          // Perform the merge.
          debugger;
          return R.mergeDeepRight(leftThing, rightThing);
        } else {
          return rightThing || leftThing;
        }
      },
    );

    console.log(res);
    return res;
  } else {
    return right;
  }
};

function setOrMergeList(existing: List | undefined, newList: List | undefined) {
  if (existing) {
    return R.mergeDeepWithKey(mergeThingLists, existing, newList);
  } else if (newList) {
    return newList;
  }
}

const handleListRetrieveSuccess = handleAction<
  ListRetrieveSuccessAction,
  State
>(LIST_RETRIEVE_SUCCESS, (state, action) => {
  let listId = action.payload!!.id;
  let newList = setOrMergeList(state.listsById[listId], action.payload);

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
  };
});

const handleUserRetrieve = handleAction<ListRetrieveAllSuccessAction, State>(
  LIST_RETRIEVE_ALL_SUCCESS,
  (state, action) => {
    let newListsById: ListsByIdMap = {};
    if (action.payload && action.payload.lists) {
      try {
        newListsById = R.reduce(
          (newListsById, list) => {
            return {
              ...newListsById,
              [list.id]: setOrMergeList(state.listsById[list.id], list),
            };
          },
          {} as ListsByIdMap,
          action.payload.lists,
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
