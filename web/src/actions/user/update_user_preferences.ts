import {
  actionChannel,
  all,
  call,
  put,
  select,
  take,
} from '@redux-saga/core/effects';
import { UserSelf } from '../../reducers/user';
import { AppState } from '../../reducers';
import { createAction } from '../utils';
import { updateUser } from './update_user';
import { FSA } from 'flux-standard-action';
import { UserPreferences } from '../../types';
import { logEvent, logException } from '../../utils/analytics';

export const USER_SELF_UPDATE_PREFS = 'user/self/update_prefs/INITIATED';
export const USER_SELF_UPDATE_PREFS_SUCCESS = 'user/self/update_prefs/SUCCESS';

export type UserUpdatePrefsAction = FSA<
  typeof USER_SELF_UPDATE_PREFS,
  UserPreferences
>;

export const updateUserPreferences = createAction<UserUpdatePrefsAction>(
  USER_SELF_UPDATE_PREFS,
);

export const updateUserPreferencesSaga = function*() {
  const chan = yield actionChannel(USER_SELF_UPDATE_PREFS);
  while (true) {
    const { payload }: UserUpdatePrefsAction = yield take(chan);

    if (payload) {
      try {
        let currUser: UserSelf | undefined = yield select(
          (state: AppState) => state.userSelf!.self,
        );

        if (currUser) {
          let newUser: UserSelf = {
            ...currUser,
            preferences: payload,
          };

          yield all([
            put(updateUser(newUser)),
            call(logEvent, 'User Settings', 'Update preferences'),
          ]);
        }
      } catch (e) {
        call(logException, `${e}`, false);
      }
    } else {
      // TODO: Error
    }
  }
};
