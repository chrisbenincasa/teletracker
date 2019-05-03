import {
  put,
  select,
  takeLatest,
  takeEvery,
  actionChannel,
  take,
} from '@redux-saga/core/effects';
import { ApiResponse } from 'apisauce';
import { FSA } from 'flux-standard-action';
import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
  USER_SELF_ADD_NETWORK,
  USER_SELF_UPDATE,
  USER_SELF_UPDATE_PREFS,
  USER_SELF_CREATE_LIST,
  USER_SELF_CREATE_LIST_SUCCESS,
} from '../constants/user';
import { AppState } from '../reducers';
import { User, Network, UserPreferences } from '../types';
import {
  DataResponse,
  TeletrackerApi,
  TeletrackerResponse,
} from '../utils/api-client';
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

export type UserActionTypes =
  | UserSelfRetrieveInitiatedAction
  | UserSelfRetrieveSuccessAction
  | UserAddNetworkAction
  | UserUpdateAction
  | UserUpdatePrefsAction
  | UserCreateListAction
  | UserCreateListSuccessAction;

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

export const retrieveUser = (force: boolean = false) => {
  return RetrieveUserSelfInitiated({ force });
};
