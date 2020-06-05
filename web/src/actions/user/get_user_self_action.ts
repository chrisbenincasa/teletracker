import { call, put, take, takeLeading } from '@redux-saga/core/effects';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { FSA } from 'flux-standard-action';
import { Network, UserPreferences } from '../../types';
import Auth, { CognitoUser } from '@aws-amplify/auth';
import { LOGIN_SUCCESSFUL } from '../auth';
import { logException } from '../../utils/analytics';

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
  // user: CognitoUser;
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
  function* self(force: boolean) {
    // let currState: AppState = yield select();

    // If the user exists on the state already, and we're not forcing reload,
    // return.
    // if (currState.userSelf.self && currState.userSelf.self.user && !force) {
    //   return { user: currState.userSelf.self.user };
    // } else {
    // }
    try {
      // Attempt to get the user from Amplify
      let user: CognitoUser = yield call(
        [Auth, Auth.currentAuthenticatedUser],
        {
          bypassCache: false,
        },
      );

      // If we don't have a user, wait until a login event and try again.
      if (user) {
        return { user };
      } else {
        yield take(LOGIN_SUCCESSFUL);
        return yield self(force);
      }
    } catch (e) {
      if (e === 'not authenticated') {
        yield take(LOGIN_SUCCESSFUL);
        return yield self(force);
      }
    }
  }

  yield takeLeading(USER_SELF_RETRIEVE_INITIATED, function*({
    payload: { force } = { force: false },
  }: UserSelfRetrieveInitiatedAction) {
    try {
      let { user } = yield self(force);
      if (!user) {
        yield put(RetrieveUserSelfEmpty());
      } else {
        let metadata = yield clientEffect(client => client.getUserSelf);
        if (metadata.ok && metadata.data) {
          yield put(
            RetrieveUserSelfSuccess({
              // user: user as CognitoUser,
              preferences: metadata.data.data.preferences,
              networks: metadata.data.data.networkPreferences,
            }),
          );
        }
      }
    } catch (e) {
      console.error(e);
      call(logException, `${e}`, false);
    }
  });
};

export const getUserSelf = (force: boolean = false) => {
  return RetrieveUserSelfInitiated({ force });
};
