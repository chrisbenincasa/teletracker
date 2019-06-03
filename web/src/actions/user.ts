import {
  actionChannel,
  put,
  select,
  take,
  takeEvery,
  takeLatest,
} from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
import { FSA } from 'flux-standard-action';
import {
  USER_SELF_ADD_NETWORK,
  USER_SELF_CREATE_LIST,
  USER_SELF_CREATE_LIST_SUCCESS,
  USER_SELF_DELETE_LIST,
  USER_SELF_DELETE_LIST_SUCCESS,
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
  USER_SELF_UPDATE,
  USER_SELF_UPDATE_ITEM_TAGS,
  USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
  USER_SELF_UPDATE_PREFS,
  USER_SELF_REMOVE_ITEM_TAGS,
  USER_SELF_REMOVE_ITEM_TAGS_SUCCESS,
} from '../constants/user';
import { AppState } from '../reducers';
import { ActionType, Network, User, UserPreferences } from '../types';
import { DataResponse, TeletrackerResponse } from '../utils/api-client';
import { clientEffect, createAction } from './utils';

interface UserSelfRetrieveInitiatedPayload {
  force: boolean;
}

export type UserSelfRetrieveInitiatedAction = FSA<
  typeof USER_SELF_RETRIEVE_INITIATED,
  UserSelfRetrieveInitiatedPayload
>;

export type UserSelfRetrieveSuccessAction = FSA<
  typeof USER_SELF_RETRIEVE_SUCCESS,
  User
>;

export interface UserAddNetworkPayload {
  network: Network;
}

export type UserAddNetworkAction = FSA<
  typeof USER_SELF_ADD_NETWORK,
  UserAddNetworkPayload
>;

export type UserUpdateAction = FSA<typeof USER_SELF_UPDATE, User>;

export type UserUpdatePrefsAction = FSA<
  typeof USER_SELF_UPDATE_PREFS,
  UserPreferences
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

export interface UserUpdateItemTagsPayload {
  thingId: number;
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
  | UserAddNetworkAction
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

export const addNetworkForUser = createAction<UserAddNetworkAction>(
  USER_SELF_ADD_NETWORK,
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

export const retrieveUserSaga = function*() {
  yield takeLatest(USER_SELF_RETRIEVE_INITIATED, function*({
    payload: { force } = { force: false },
  }: UserSelfRetrieveInitiatedAction) {
    let currState: AppState = yield select();

    if (currState.userSelf.self && !force) {
      yield put(RetrieveUserSelfSuccess(currState.userSelf.self));
    } else {
      try {
        let response: ApiResponse<DataResponse<User>> = yield clientEffect(
          client => client.getUserSelf,
        );

        if (response.ok && response.data) {
          yield put(RetrieveUserSelfSuccess(response.data!.data));
        } else {
          // TODO handle error
        }
      } catch (e) {
        // TODO handle error
      }
    }
  });
};

export const addNetworkToUserSaga = function*() {
  yield takeEvery(USER_SELF_ADD_NETWORK, function*({
    payload,
  }: UserAddNetworkAction) {
    if (payload) {
      let currUser: User | undefined = yield select(
        (state: AppState) => state.userSelf!.self,
      );

      if (!currUser) {
        // TODO: Fail
      } else {
        let newSubs = [...currUser.networkSubscriptions, payload.network];
        let newUser: User = {
          ...currUser,
          networkSubscriptions: newSubs,
        };

        let response: TeletrackerResponse<User> = yield clientEffect(
          client => client.updateUserSelf,
          newUser,
        );

        if (response.ok) {
          yield put(RetrieveUserSelfSuccess(response.data!.data));
        } else {
          // TODO: Handle error
        }
      }
    }
  });
};

export const updateUserPreferencesSaga = function*() {
  const chan = yield actionChannel(USER_SELF_UPDATE_PREFS);
  while (true) {
    const { payload }: UserUpdatePrefsAction = yield take(chan);

    if (payload) {
      let currUser: User | undefined = yield select(
        (state: AppState) => state.userSelf!.self,
      );

      if (currUser) {
        let newUser: User = {
          ...currUser,
          userPreferences: payload,
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
      let response: TeletrackerResponse<User> = yield clientEffect(
        client => client.updateUserSelf,
        payload,
      );

      if (response.ok) {
        yield put(RetrieveUserSelfSuccess(response.data!.data));
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
        yield put(RetrieveUserSelfInitiated({ force: true }));
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
      console.log(response);
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
