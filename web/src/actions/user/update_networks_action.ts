import { all, call, put, select, takeEvery } from '@redux-saga/core/effects';
import { UserSelf } from '../../reducers/user';
import { AppState } from '../../reducers';
import * as R from 'ramda';
import { Network } from '../../types';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { updateUser } from './update_user';
import { logEvent, logException } from '../../utils/analytics';

export const USER_SELF_UPDATE_NETWORKS = 'user/self/networks/UPDATE';
export const USER_SELF_UPDATE_NETWORKS_SUCCESS =
  'user/self/networks/UPDATE_SUCCESS';

export const updateNetworksForUser = createAction<UserUpdateNetworksAction>(
  USER_SELF_UPDATE_NETWORKS,
);

export interface UserUpdateNetworksPayload {
  add: Network[];
  remove: Network[];
}

export type UserUpdateNetworksAction = FSA<
  typeof USER_SELF_UPDATE_NETWORKS,
  UserUpdateNetworksPayload
>;

export const updateNetworksForUserSaga = function*() {
  yield takeEvery(USER_SELF_UPDATE_NETWORKS, function*({
    payload,
  }: UserUpdateNetworksAction) {
    if (payload) {
      try {
        let currUser: UserSelf | undefined = yield select(
          (state: AppState) => state.userSelf!.self,
        );

        if (!currUser) {
          // TODO: Fail
        } else {
          let existingIds = R.map(R.prop('id'), currUser.networks || []);
          let removeIds = R.map(R.prop('id'), payload.remove);
          let subsRemoved = R.reject(
            sub => R.contains(sub.id, removeIds),
            currUser.networks || [],
          );

          let newSubs = R.concat(
            subsRemoved,
            R.reject(sub => R.contains(sub.id, existingIds), payload.add),
          );

          let newUser: UserSelf = {
            ...currUser,
            networks: newSubs,
          };

          yield all([
            put(updateUser(newUser)),
            call(logEvent, 'User Settings', 'Update networks'),
          ]);
        }
      } catch (e) {
        yield call(logException, `${e}`, false);
      }
    }
  });
};
