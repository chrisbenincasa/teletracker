import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
  USER_SELF_UPDATE_NETWORKS,
  USER_SELF_UPDATE_SUCCESS,
  UserActionTypes,
  UserSelfRetrieveEmptyAction,
  UserSelfRetrieveSuccessAction,
  UserUpdateSuccessAction,
} from '../actions/user';
import { Network, UserPreferences } from '../types';
import { flattenActions, handleAction } from './utils';
import {
  USER_SELF_CREATE_LIST,
  USER_SELF_CREATE_LIST_SUCCESS,
  USER_SELF_DELETE_LIST,
  USER_SELF_DELETE_LIST_SUCCESS,
  USER_SELF_UPDATE_LIST,
  USER_SELF_UPDATE_LIST_SUCCESS,
} from '../actions/lists';
import {
  LOGOUT_SUCCESSFUL,
  USER_STATE_CHANGE,
  UserStateChangeAction,
} from '../actions/auth';
import { CognitoUser } from '@aws-amplify/auth';
import produce from 'immer';
import { Draft } from 'immer/dist/types/types-external';

export type Loading = { [X in UserActionTypes['type']]: boolean };

export interface UserSelf {
  readonly user?: CognitoUser;
  readonly preferences?: UserPreferences;
  readonly networks?: Network[];
}

export interface State {
  readonly retrievingSelf: boolean;
  readonly self?: UserSelf;
  readonly updatingSelf: boolean;
  readonly loading: Partial<Loading>;
}

const initialState: State = {
  retrievingSelf: false,
  updatingSelf: false,
  loading: {},
};

// const stateChange = handleAction(
//   USER_STATE_CHANGE,
//   produce((state: Draft<State>, { payload }: UserStateChangeAction) => {
//     let nextSelf = state.self || {};
//     if (payload) {
//       nextSelf.user = payload;
//     }
//
//     state.self = nextSelf;
//   }),
// );

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
  (
    state: State,
    action: UserSelfRetrieveSuccessAction | UserSelfRetrieveEmptyAction,
  ) => {
    if (action.payload) {
      return {
        ...state,
        retrievingSelf: false,
        // self: action.payload,
      };
    } else {
      return {
        ...state,
        retrievingSelf: false,
        // self: undefined,
      } as State;
    }
  },
);

const updateUserMetadataSuccess = handleAction(
  USER_SELF_UPDATE_SUCCESS,
  (state: State, action: UserUpdateSuccessAction) => {
    if (action.payload) {
      return {
        ...state,
        self: {
          ...(state.self || {}),
          preferences: action.payload.preferences,
          networks: action.payload.networks,
        },
      } as State;
    } else {
      return state;
    }
  },
);

const logoutUser = handleAction(LOGOUT_SUCCESSFUL, (state: State) => {
  return {
    ...state,
    self: undefined,
  } as State;
});

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

const userRenameList = handleAction(USER_SELF_UPDATE_LIST, (state: State) => {
  return {
    ...state,
    loading: {
      ...state.loading,
      [USER_SELF_UPDATE_LIST]: true,
    },
  } as State;
});

const userRenameListSuccess = handleAction(
  USER_SELF_UPDATE_LIST_SUCCESS,
  (state: State) => {
    return {
      ...state,
      loading: {
        ...state.loading,
        [USER_SELF_UPDATE_LIST]: false,
      },
    } as State;
  },
);

export default flattenActions(
  'user',
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
  logoutUser,
  updateUserMetadataSuccess,
  // stateChange,
);
