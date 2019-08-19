import { call, put, select, take, takeLeading } from '@redux-saga/core/effects';
import { AppState } from '../../reducers';
import * as firebase from 'firebase';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { authStateChannelMaker } from '../auth/watch_auth_state';
import { FSA } from 'flux-standard-action';
import { Network, UserPreferences } from '../../types';

export const USER_SELF_RETRIEVE_INITIATED = 'user/self/retrieve/INITIATED';
export const USER_SELF_RETRIEVE_SUCCESS = 'user/self/retrieve/SUCCESSFUL';
export const USER_SELF_RETRIEVE_FAIL = 'user/self/retrieve/FAILED';

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

export const RetrieveUserSelfInitiated = createAction<
  UserSelfRetrieveInitiatedAction
>(USER_SELF_RETRIEVE_INITIATED);

export const RetrieveUserSelfSuccess = createAction<
  UserSelfRetrieveSuccessAction
>(USER_SELF_RETRIEVE_SUCCESS);

export const RetrieveUserSelfEmpty = createBasicAction<
  UserSelfRetrieveEmptyAction
>(USER_SELF_RETRIEVE_SUCCESS);

export const getUserSelfSaga = function*() {
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

export const getUserSelf = (force: boolean = false) => {
  return RetrieveUserSelfInitiated({ force });
};
