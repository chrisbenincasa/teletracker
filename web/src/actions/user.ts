import { Dispatch } from 'redux';
import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
} from '../constants/user';
import { User } from '../types';
import { TeletrackerApi } from '../utils/api-client';
import { AppState } from '../reducers';

interface UserSelfRetrieveInitiatedAction {
  type: typeof USER_SELF_RETRIEVE_INITIATED;
}

interface UserSelfRetrieveSuccessAction {
  type: typeof USER_SELF_RETRIEVE_SUCCESS;
  payload: User;
}

export type UserActionTypes =
  | UserSelfRetrieveInitiatedAction
  | UserSelfRetrieveSuccessAction;

export const retrieveUserSelfInitiated = {
  type: USER_SELF_RETRIEVE_INITIATED,
};

export const retrieveUserSelfSuccess = (payload: User) => ({
  type: USER_SELF_RETRIEVE_SUCCESS,
  payload,
});

const client = TeletrackerApi.instance;

export const retrieveUser = () => {
  return async (dispatch: Dispatch, stateFn: () => AppState) => {
    let currState = stateFn();

    if (currState.userSelf.self) {
      dispatch(retrieveUserSelfSuccess(currState.userSelf.self));
    } else {
      dispatch(retrieveUserSelfInitiated);

      return client.getUserSelf().then(response => {
        if (response.ok && response.data) {
          dispatch(retrieveUserSelfSuccess(response.data!.data));
        }
      });
    }
  };
};
