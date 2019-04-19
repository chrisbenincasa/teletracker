import { ListActions, ListRetrieveSuccess } from '../actions/lists';
import {
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_ADD_ITEM_FAILED,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
} from '../constants/lists';
import { List } from '../types';
import { handleAction, handleActions } from 'redux-actions';
import { combineReducers } from 'redux';

export interface ListOperationState {
  inProgress: boolean;
  operationType?: string;
}

export interface State {
  operation: ListOperationState;
  listsById: ListsByIdMap;
}

export interface ListsByIdMap {
  [key: number]: List;
}

const initialState: State = {
  operation: {
    inProgress: false,
  },
  listsById: {},
};

const handleListAddInitiated = handleAction<State, {}>(
  LIST_ADD_ITEM_INITIATED,
  state => {
    return {
      ...state,
      operation: {
        ...state.operation,
        operationType: LIST_ADD_ITEM_INITIATED,
        inProgress: true,
      },
    };
  },
  initialState,
);

const handleListSuccess = handleAction<State, List>(
  LIST_RETRIEVE_SUCCESS,
  (state, action) => {
    state.listsById[action.payload.id] = action.payload;

    return {
      ...state,
      operation: {
        ...state.operation,
        operationType: undefined,
        inProgress: false,
      },
    };
  },
  initialState,
);

handleActions(
  {
    LIST_RETRIEVE_SUCCESS: handleListSuccess,
  },
  initialState,
);

export default function listReducer(
  state: State = initialState,
  action: ListActions,
): State {
  switch (action.type) {
    case LIST_ADD_ITEM_INITIATED:
      return {
        ...state,
        operation: {
          ...state.operation,
          operationType: LIST_ADD_ITEM_INITIATED,
          inProgress: true,
        },
      };
    case LIST_ADD_ITEM_SUCCESS:
    case LIST_ADD_ITEM_FAILED:
      let state1 = {
        ...state,
        operation: {
          ...state.operation,
          operationType: undefined,
          inProgress: false,
        },
      };

      if (action.type == LIST_ADD_ITEM_SUCCESS) {
        return state1;
      } else {
        return state1;
      }

    case LIST_RETRIEVE_INITIATED:
      return {
        ...state,
        operation: {
          ...state.operation,
          operationType: LIST_RETRIEVE_INITIATED,
          inProgress: true,
        },
      };

    case LIST_RETRIEVE_SUCCESS:
      let payload = action.payload as List;
      state.listsById[payload.id] = payload;

      return {
        ...state,
        operation: {
          ...state.operation,
          operationType: undefined,
          inProgress: false,
        },
        listsById: state.listsById,
      };

    default:
      return state;
  }
}
