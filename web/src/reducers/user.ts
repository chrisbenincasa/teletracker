import { User } from '../types';
import { UserActionTypes } from '../actions/user';
import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
} from '../constants/user';

export interface State {
  retrievingSelf: boolean;
  self?: User;
}

const initialState: State = {
  retrievingSelf: false,
};

export default function userReducer(
  state: State = initialState,
  action: UserActionTypes,
): State {
  switch (action.type) {
    case USER_SELF_RETRIEVE_INITIATED:
      return {
        ...state,
        retrievingSelf: true,
      };
    case USER_SELF_RETRIEVE_SUCCESS:
      return {
        ...state,
        retrievingSelf: false,
        self: action.payload,
      };
    default:
      return state;
  }
}
