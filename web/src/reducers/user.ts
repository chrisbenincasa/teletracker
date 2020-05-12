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
  ListActions,
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
import { List, Record, RecordOf } from 'immutable';

export type LoadingType = { [X in UserActionTypes['type']]?: boolean };
export type Loading = RecordOf<LoadingType>;
const makeLoading = Record<LoadingType>({});

export type UserSelfType = {
  user?: CognitoUser;
  preferences?: UserPreferences;
  networks?: List<Network>;
};

export type UserSelf = RecordOf<UserSelfType>;

const makeUserSelf = Record<UserSelfType>({});

type StateType = {
  retrievingSelf: boolean;
  self?: UserSelf;
  updatingSelf: boolean;
  loading: Loading;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {
  retrievingSelf: false,
  updatingSelf: false,
  loading: makeLoading(),
};

const makeState = Record(initialState);

const stateChange = handleAction(
  USER_STATE_CHANGE,
  (state: State, { payload }: UserStateChangeAction) => {
    let nextSelf: UserSelf = state.self || makeUserSelf();
    if (payload) {
      nextSelf = nextSelf.set('user', payload);
    }

    return state.set('self', nextSelf);
  },
);

const selfRetrieveInitiated = handleAction(
  USER_SELF_RETRIEVE_INITIATED,
  (state: State) => {
    return state.set('retrievingSelf', true);
  },
);

const selfRetrieveSuccess = handleAction(
  USER_SELF_RETRIEVE_SUCCESS,
  (
    state: State,
    action: UserSelfRetrieveSuccessAction | UserSelfRetrieveEmptyAction,
  ) => {
    if (action.payload) {
      return state.merge({
        retrievingSelf: false,
        self: (state.self || makeUserSelf()).merge({
          user: action.payload.user,
          networks: List(action.payload.networks),
          preferences: action.payload.preferences,
        }),
      });
    } else {
      return state.merge({
        retrievingSelf: false,
        self: undefined,
      });
    }
  },
);

const updateUserMetadataSuccess = handleAction(
  USER_SELF_UPDATE_SUCCESS,
  (state: State, action: UserUpdateSuccessAction) => {
    if (action.payload) {
      return state.set(
        'self',
        (state.self || makeUserSelf()).merge(action.payload),
      );
    } else {
      return state;
    }
  },
);

const logoutUser = handleAction(LOGOUT_SUCCESSFUL, (state: State) => {
  return state.remove('self');
});

const userUpdateNetworks = handleAction(
  USER_SELF_UPDATE_NETWORKS,
  (state: State) => {
    return state.set('updatingSelf', true);
  },
);

const userCreateList = handleAction(USER_SELF_CREATE_LIST, (state: State) => {
  return state.set('loading', state.loading.set(USER_SELF_CREATE_LIST, true));
});

const userCreateListSuccess = handleAction(
  USER_SELF_CREATE_LIST_SUCCESS,
  (state: State) => {
    return state.set(
      'loading',
      state.loading.set(USER_SELF_CREATE_LIST, false),
    );
  },
);

const userDeleteList = handleAction(USER_SELF_DELETE_LIST, (state: State) => {
  return state.set('loading', state.loading.set(USER_SELF_DELETE_LIST, true));
});

const userDeleteListSuccess = handleAction(
  USER_SELF_DELETE_LIST_SUCCESS,
  (state: State) => {
    return state.set(
      'loading',
      state.loading.set(USER_SELF_DELETE_LIST, false),
    );
  },
);

const userRenameList = handleAction(USER_SELF_UPDATE_LIST, (state: State) => {
  return state.set('loading', state.loading.set(USER_SELF_UPDATE_LIST, true));
});

const userRenameListSuccess = handleAction(
  USER_SELF_UPDATE_LIST_SUCCESS,
  (state: State) => {
    return state.set(
      'loading',
      state.loading.set(USER_SELF_UPDATE_LIST, false),
    );
  },
);

export default {
  initialState: makeState(),
  reducer: flattenActions(
    'user',
    makeState(),
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
    stateChange,
  ),
};
