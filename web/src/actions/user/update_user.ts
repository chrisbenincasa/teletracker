import { actionChannel, put, take } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { UserDetails } from '../../types';
import { clientEffect, createAction } from '../utils';
import { UserSelf } from '../../reducers/user';
import { FSA } from 'flux-standard-action';

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
