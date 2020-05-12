import { put, select, takeEvery } from '@redux-saga/core/effects';
import { UserSelf } from '../../reducers/user';
import { AppState } from '../../reducers';
import * as R from 'ramda';
import { Network } from '../../types';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { updateUser } from './update_user';
import ReactGA from 'react-ga';
import { List } from 'immutable';

export const USER_SELF_UPDATE_NETWORKS = 'user/self/networks/UPDATE';
export const USER_SELF_UPDATE_NETWORKS_SUCCESS =
  'user/self/networks/UPDATE_SUCCESS';

export const updateNetworksForUser = createAction<UserUpdateNetworksAction>(
  USER_SELF_UPDATE_NETWORKS,
);

export interface UserUpdateNetworksPayload {
  add: List<Network>;
  remove: List<Network>;
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
      let currUser: UserSelf | undefined = yield select(
        (state: AppState) => state.userSelf!.self,
      );

      if (!currUser) {
        // TODO: Fail
      } else {
        // let existingIds = (currUser.networks || List<Network>()).map(
        //   net => net.id,
        // );
        // let removeIds = payload.remove.map(x => x.id);
        // let subsRemoved: List<Network> = (
        //   currUser.networks || List()
        // ).filterNot(network => removeIds.contains(network.id));
        //
        // subsRemoved.concat(payload.add);
        //
        // let newSubs = R.concat(
        //   subsRemoved,
        //   R.reject(sub => R.contains(sub.id, existingIds), payload.add),
        // );
        //
        // let newUser: UserSelf = {
        //   ...currUser,
        //   networks: newSubs,
        // };
        //
        // yield put(updateUser(newUser));
        //
        // ReactGA.event({
        //   category: 'User',
        //   action: 'Updated networks',
        // });
      }
    }
  });
};
