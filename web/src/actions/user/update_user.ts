import { actionChannel, all, call, put, take } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { UserDetails } from '../../types';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { UserSelf } from '../../reducers/user';
import { FSA } from 'flux-standard-action';
import { logEvent, logException } from '../../utils/analytics';

export type UserUpdateSuccessPayload = Omit<UserSelf, 'user'>;

export const USER_SELF_UPDATE = 'user/self/update/INITIATED';
export const USER_SELF_UPDATE_SUCCESS = 'user/self/update/SUCCESS';

export type UserUpdateAction = FSA<
  typeof USER_SELF_UPDATE,
  Partial<Omit<UserSelf, 'user'>>
>;

export type UserUpdateSuccessAction = FSA<
  typeof USER_SELF_UPDATE_SUCCESS,
  UserUpdateSuccessPayload
>;

export const updateUser = createAction<UserUpdateAction>(USER_SELF_UPDATE);

export const updateUserSuccess = createAction<UserUpdateSuccessAction>(
  USER_SELF_UPDATE_SUCCESS,
);

export const updateUserSaga = function*() {
  const chan = yield actionChannel(USER_SELF_UPDATE);
  while (true) {
    const { payload }: UserUpdateAction = yield take(chan);
    if (payload) {
      try {
        let response: TeletrackerResponse<UserDetails> = yield clientEffect(
          client => client.updateUserSelf,
          payload.networks,
          payload.preferences,
        );

        if (response.ok) {
          yield all([
            put(
              updateUserSuccess({
                networks: response.data!.data.networkPreferences,
                preferences: response.data!.data.preferences,
              }),
            ),
          ]);
        } else {
          // TODO: Error
        }
      } catch (e) {
        call(logException, `${e}`, false);
      }
    } else {
      // TODO: Error
    }
  }
};
