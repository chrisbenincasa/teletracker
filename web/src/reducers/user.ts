import {
  UserActionTypes,
  UserSelfRetrieveSuccessAction,
} from '../actions/user';
import {
  USER_SELF_CREATE_LIST,
  USER_SELF_CREATE_LIST_SUCCESS,
  USER_SELF_DELETE_LIST,
  USER_SELF_DELETE_LIST_SUCCESS,
  USER_SELF_RENAME_LIST,
  USER_SELF_RENAME_LIST_SUCCESS,
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
  USER_SELF_UPDATE_NETWORKS,
} from '../constants/user';
import { User } from '../types';
import { flattenActions, handleAction } from './utils';

export type Loading = { [X in UserActionTypes['type']]: boolean };

export interface State {
  retrievingSelf: boolean;
  self: User | undefined;
  updatingSelf: boolean;
  loading: Partial<Loading>;
}

const initialState: State = {
  retrievingSelf: false,
  self: undefined,
  updatingSelf: false,
  loading: {},
};

const selfRetrieveInitiated = handleAction(
  USER_SELF_RETRIEVE_INITIATED,
  (state: State) => {
    return {
      ...state,
      retrievingSelf: true,
    };
  },
);

const selfRetrieveSuccess = handleAction(
  USER_SELF_RETRIEVE_SUCCESS,
  (state: State, action: UserSelfRetrieveSuccessAction) => {
    return {
      ...state,
      retrievingSelf: false,
      self: action.payload,
    };
  },
);

const userUpdateNetworks = handleAction(
  USER_SELF_UPDATE_NETWORKS,
  (state: State) => {
    return {
      ...state,
      updatingSelf: true,
    } as State;
  },
);

const userCreateList = handleAction(USER_SELF_CREATE_LIST, (state: State) => {
  return {
    ...state,
    loading: {
      ...state.loading,
      [USER_SELF_CREATE_LIST]: true,
    },
  } as State;
});

const userCreateListSuccess = handleAction(
  USER_SELF_CREATE_LIST_SUCCESS,
  (state: State) => {
    return {
      ...state,
      loading: {
        ...state.loading,
        [USER_SELF_CREATE_LIST]: false,
      },
    } as State;
  },
);

const userDeleteList = handleAction(USER_SELF_DELETE_LIST, (state: State) => {
  return {
    ...state,
    loading: {
      ...state.loading,
      [USER_SELF_DELETE_LIST]: true,
    },
  } as State;
});

const userDeleteListSuccess = handleAction(
  USER_SELF_DELETE_LIST_SUCCESS,
  (state: State) => {
    return {
      ...state,
      loading: {
        ...state.loading,
        [USER_SELF_DELETE_LIST]: false,
      },
    } as State;
  },
);

const userRenameList = handleAction(USER_SELF_RENAME_LIST, (state: State) => {
  return {
    ...state,
    loading: {
      ...state.loading,
      [USER_SELF_RENAME_LIST]: true,
    },
  } as State;
});

const userRenameListSuccess = handleAction(
  USER_SELF_RENAME_LIST_SUCCESS,
  (state: State) => {
    return {
      ...state,
      loading: {
        ...state.loading,
        [USER_SELF_RENAME_LIST]: false,
      },
    } as State;
  },
);

export default flattenActions(
  initialState,
  selfRetrieveInitiated,
  selfRetrieveSuccess,
  userUpdateNetworks,
  userCreateList,
  userCreateListSuccess,
  userDeleteList,
  userDeleteListSuccess,
  userRenameList,
  userRenameListSuccess,
);
