import {
  actionChannel,
  call,
  put,
  select,
  take,
  takeEvery,
  takeLeading,
} from '@redux-saga/core/effects';
import * as firebase from 'firebase/app';
import { FSA } from 'flux-standard-action';
import * as R from 'ramda';
import { END, eventChannel } from 'redux-saga';
import {
  USER_SELF_CREATE_LIST,
  USER_SELF_CREATE_LIST_SUCCESS,
  USER_SELF_DELETE_LIST,
  USER_SELF_DELETE_LIST_SUCCESS,
  USER_SELF_REMOVE_ITEM_TAGS,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  USER_SELF_RENAME_LIST,
  USER_SELF_RENAME_LIST_SUCCESS,
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
  USER_SELF_UPDATE,
  USER_SELF_UPDATE_ITEM_TAGS,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  USER_SELF_UPDATE_NETWORKS,
  USER_SELF_UPDATE_PREFS,
  USER_SELF_UPDATE_SUCCESS,
} from '../constants/user';
import { AppState } from '../reducers';
import {
  ActionType,
  Network,
  User,
  UserDetails,
  UserPreferences,
} from '../types';
import TeletrackerApi, { TeletrackerResponse } from '../utils/api-client';
import { retrieveAllLists } from './lists';
import { clientEffect, createAction, createBasicAction } from './utils';
import { UnsetToken } from './auth';
import { UserSelf } from '../reducers/user';

interface UserSelfRetrieveInitiatedPayload {
  force: boolean;
}

export type UserSelfRetrieveInitiatedAction = FSA<
  typeof USER_SELF_RETRIEVE_INITIATED,
  UserSelfRetrieveInitiatedPayload
>;

export interface UserSelfRetrievePayload {
  user: firebase.User;
  preferences: UserPreferences;
  networks: Network[];
}

export type UserSelfRetrieveSuccessAction = FSA<
  typeof USER_SELF_RETRIEVE_SUCCESS,
  UserSelfRetrievePayload
>;

export type UserSelfRetrieveEmptyAction = FSA<
  typeof USER_SELF_RETRIEVE_SUCCESS
>;

export interface UserUpdateNetworksPayload {
  add: Network[];
  remove: Network[];
}

export type UserUpdateNetworksAction = FSA<
  typeof USER_SELF_UPDATE_NETWORKS,
  UserUpdateNetworksPayload
>;

export type UserUpdateAction = FSA<
  typeof USER_SELF_UPDATE,
  Partial<Omit<UserSelf, 'user'>>
>;

export type UserUpdatePrefsAction = FSA<
  typeof USER_SELF_UPDATE_PREFS,
  UserPreferences
>;

export type UserUpdateSuccessPayload = Omit<UserSelf, 'user'>;

export type UserUpdateSuccessAction = FSA<
  typeof USER_SELF_UPDATE_SUCCESS,
  UserUpdateSuccessPayload
>;

export interface UserCreateListPayload {
  name: string;
}

export type UserCreateListAction = FSA<
  typeof USER_SELF_CREATE_LIST,
  UserCreateListPayload
>;

export type UserCreateListSuccessAction = FSA<
  typeof USER_SELF_CREATE_LIST_SUCCESS,
  { id: number }
>;

export interface UserDeleteListPayload {
  listId: number;
  mergeListId?: number;
}

export type UserDeleteListAction = FSA<
  typeof USER_SELF_DELETE_LIST,
  UserDeleteListPayload
>;

export type UserDeleteListSuccessAction = FSA<
  typeof USER_SELF_DELETE_LIST_SUCCESS,
  UserDeleteListPayload
>;

export interface UserRenameListPayload {
  listId: number;
  listName: string;
}

export type UserRenameListAction = FSA<
  typeof USER_SELF_RENAME_LIST,
  UserRenameListPayload
>;

export type UserRenameListSuccessAction = FSA<
  typeof USER_SELF_RENAME_LIST_SUCCESS,
  UserRenameListPayload
>;

export interface UserUpdateItemTagsPayload {
  thingId: string;
  action: ActionType;
  value?: number;
  lazy?: boolean; // If true, requires the server call to complete before updating state.
}

export type UserUpdateItemTagsAction = FSA<
  typeof USER_SELF_UPDATE_ITEM_TAGS,
  UserUpdateItemTagsPayload
>;

export type UserUpdateItemTagsSuccessAction = FSA<
  typeof USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  UserUpdateItemTagsPayload
>;

export type UserRemoveItemTagsAction = FSA<
  typeof USER_SELF_REMOVE_ITEM_TAGS,
  UserUpdateItemTagsPayload
>;

export type UserRemoveItemTagsSuccessAction = FSA<
  typeof USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
  UserUpdateItemTagsPayload
>;

export type UserActionTypes =
  | UserSelfRetrieveInitiatedAction
  | UserSelfRetrieveSuccessAction
  | UserUpdateNetworksAction
  | UserUpdateAction
  | UserUpdatePrefsAction
  | UserCreateListAction
  | UserCreateListSuccessAction
  | UserUpdateItemTagsAction
  | UserUpdateItemTagsSuccessAction;

export const RetrieveUserSelfInitiated = createAction<
  UserSelfRetrieveInitiatedAction
>(USER_SELF_RETRIEVE_INITIATED);

export const RetrieveUserSelfSuccess = createAction<
  UserSelfRetrieveSuccessAction
>(USER_SELF_RETRIEVE_SUCCESS);

export const RetrieveUserSelfEmpty = createBasicAction<
  UserSelfRetrieveEmptyAction
>(USER_SELF_RETRIEVE_SUCCESS);

export const updateNetworksForUser = createAction<UserUpdateNetworksAction>(
  USER_SELF_UPDATE_NETWORKS,
);

export const updateUserPreferences = createAction<UserUpdatePrefsAction>(
  USER_SELF_UPDATE_PREFS,
);

export const createList = createAction<UserCreateListAction>(
  USER_SELF_CREATE_LIST,
);

export const createListSuccess = createAction<UserCreateListSuccessAction>(
  USER_SELF_CREATE_LIST_SUCCESS,
);

export const deleteList = createAction<UserDeleteListAction>(
  USER_SELF_DELETE_LIST,
);

export const deleteListSuccess = createAction<UserDeleteListSuccessAction>(
  USER_SELF_DELETE_LIST_SUCCESS,
);

export const renameList = createAction<UserRenameListAction>(
  USER_SELF_RENAME_LIST,
);

export const renameListSuccess = createAction<UserRenameListSuccessAction>(
  USER_SELF_RENAME_LIST_SUCCESS,
);

export const updateUserItemTags = createAction<UserUpdateItemTagsAction>(
  USER_SELF_UPDATE_ITEM_TAGS,
);

export const updateUserItemTagsSuccess = createAction<
  UserUpdateItemTagsSuccessAction
>(USER_SELF_UPDATE_ITEM_TAGS_SUCCESS);

export const removeUserItemTags = createAction<UserRemoveItemTagsAction>(
  USER_SELF_REMOVE_ITEM_TAGS,
);

export const removeUserItemTagsSuccess = createAction<
  UserRemoveItemTagsSuccessAction
>(USER_SELF_REMOVE_ITEM_TAGS_SUCCESS);

export const updateUserSuccess = createAction<UserUpdateSuccessAction>(
  USER_SELF_UPDATE_SUCCESS,
);

export const watchAuthState = function*() {
  const authStateChannel = yield call(authStateChannelMaker);
  try {
    while (true) {
      let { user } = yield take(authStateChannel);
      if (user) {
        let token: string = yield call(() => user.getIdToken());
        yield call([TeletrackerApi, TeletrackerApi.setToken], token);
        yield put({ type: 'auth/SET_TOKEN', payload: token });
        yield put({ type: 'USER_STATE_CHANGE', payload: user });
      } else {
        yield put(UnsetToken());
      }
    }
  } catch (e) {
    console.error(e);
  }
};

const authStateChannelMaker = function*() {
  return eventChannel(emitter => {
    return firebase.auth().onAuthStateChanged(
      user => {
        emitter({ user });
      },
      err => {
        console.error(err);
      },
      () => {
        emitter(END);
      },
    );
  });
};

export const retrieveUserSaga = function*() {
  const authStateChannel = yield call(authStateChannelMaker);

  function* self(force: boolean) {
    let currState: AppState = yield select();

    if (currState.userSelf.self && currState.userSelf.self.user && !force) {
      return { user: currState.userSelf.self.user };
    } else {
      let user = firebase.auth().currentUser;
      if (user) {
        return { user };
      } else {
        return yield take(authStateChannel);
      }
    }
  }

  yield takeLeading(USER_SELF_RETRIEVE_INITIATED, function*({
    payload: { force } = { force: false },
  }: UserSelfRetrieveInitiatedAction) {
    let { user } = yield self(force);

    if (!user) {
      yield put(RetrieveUserSelfEmpty());
    } else {
      let metadata = yield clientEffect(client => client.getUserSelf);
      if (metadata.ok && metadata.data) {
        yield put(
          RetrieveUserSelfSuccess({
            user: user as firebase.User,
            preferences: metadata.data.data.preferences,
            networks: metadata.data.data.networkPreferences,
          }),
        );
      }
    }
  });
};

export const updateNetworksForUserSaga = function*() {
  yield takeEvery(USER_SELF_UPDATE_NETWORKS, function*({
    payload,
  }: UserUpdateNetworksAction) {
    if (payload) {
      let currUser: UserSelf | undefined = yield select(
        (state: AppState) => state.userSelf!.self,
      );

      if (!currUser) {
        // TODO: Fail
      } else {
        let existingIds = R.map(R.prop('id'), currUser.networks);
        let removeIds = R.map(R.prop('id'), payload.remove);
        let subsRemoved = R.reject(
          sub => R.contains(sub.id, removeIds),
          currUser.networks,
        );

        let newSubs = R.concat(
          subsRemoved,
          R.reject(sub => R.contains(sub.id, existingIds), payload.add),
        );

        let newUser: UserSelf = {
          ...currUser,
          networks: newSubs,
        };

        yield put({ type: USER_SELF_UPDATE, payload: newUser });
      }
    }
  });
};

export const updateUserPreferencesSaga = function*() {
  const chan = yield actionChannel(USER_SELF_UPDATE_PREFS);
  while (true) {
    const { payload }: UserUpdatePrefsAction = yield take(chan);

    if (payload) {
      let currUser: UserSelf | undefined = yield select(
        (state: AppState) => state.userSelf!.self,
      );

      if (currUser) {
        let newUser: UserSelf = {
          ...currUser,
          preferences: payload,
        };

        yield put({ type: USER_SELF_UPDATE, payload: newUser });
      }
    }
  }
};

export const updateUserSaga = function*() {
  const chan = yield actionChannel(USER_SELF_UPDATE);
  while (true) {
    const { payload }: UserUpdateAction = yield take(chan);
    if (payload) {
      let response: TeletrackerResponse<UserDetails> = yield clientEffect(
        client => client.updateUserSelf,
        payload.networks,
        payload.preferences,
      );

      if (response.ok) {
        yield put(
          updateUserSuccess({
            networks: response.data!.data.networkPreferences,
            preferences: response.data!.data.preferences,
          }),
        );
      }
    }
  }
};

export const createNewListSaga = function*() {
  yield takeEvery(USER_SELF_CREATE_LIST, function*({
    payload,
  }: UserCreateListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.createList,
        payload.name,
      );

      if (response.ok) {
        yield put(createListSuccess(response.data!.data));
        yield put(retrieveAllLists({}));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};

export const deleteListSaga = function*() {
  yield takeEvery(USER_SELF_DELETE_LIST, function*({
    payload,
  }: UserDeleteListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.deleteList,
        payload.listId,
        Number(payload.mergeListId),
      );

      if (response.ok) {
        yield put(
          deleteListSuccess({
            listId: payload.listId,
            mergeListId: payload.mergeListId,
          }),
        );
        yield put(RetrieveUserSelfInitiated({ force: true }));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};

export const renameListSaga = function*() {
  yield takeEvery(USER_SELF_RENAME_LIST, function*({
    payload,
  }: UserRenameListAction) {
    if (payload) {
      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.renameList,
        payload.listId,
        payload.listName,
      );

      if (response.ok) {
        yield put(
          renameListSuccess({
            listId: payload.listId,
            listName: payload.listName,
          }),
        );
        yield put(RetrieveUserSelfInitiated({ force: true }));
      } else {
        // TODO: ERROR
      }
    } else {
      // TODO: Fail
    }
  });
};

export const updateUserActionSaga = function*() {
  yield takeEvery(USER_SELF_UPDATE_ITEM_TAGS, function*({
    payload,
  }: UserUpdateItemTagsAction) {
    if (payload) {
      if (!payload.lazy) {
        yield put(updateUserItemTagsSuccess(payload));
      }

      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.updateActions,
        payload.thingId,
        payload.action,
        payload.value,
      );

      if (response.ok && payload.lazy) {
        yield put(updateUserItemTagsSuccess(payload));
      } else {
        // TODO: Error
      }
    } else {
      // TODO: Error
    }
  });
};

export const removeUserActionSaga = function*() {
  yield takeEvery(USER_SELF_REMOVE_ITEM_TAGS, function*({
    payload,
  }: UserRemoveItemTagsAction) {
    if (payload) {
      if (!payload.lazy) {
        yield put(removeUserItemTagsSuccess(payload));
      }

      let response: TeletrackerResponse<any> = yield clientEffect(
        client => client.removeActions,
        payload.thingId,
        payload.action,
      );

      if (response.ok && payload.lazy) {
        yield put(removeUserItemTagsSuccess(payload));
      } else {
        // TODO: Error
      }
    } else {
      // TODO: Error
    }
  });
};

export const retrieveUser = (force: boolean = false) => {
  return RetrieveUserSelfInitiated({ force });
};
