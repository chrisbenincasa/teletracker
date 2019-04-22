import { UserSelfRetrieveSuccessAction } from '../actions/user';
import {
  USER_SELF_RETRIEVE_INITIATED,
  USER_SELF_RETRIEVE_SUCCESS,
} from '../constants/user';
import { User } from '../types';
import { flattenActions, handleAction } from './utils';

export interface State {
  retrievingSelf: boolean;
  self: User | undefined;
}

const initialState: State = {
  retrievingSelf: false,
  self: undefined,
};

const selfRetrieveInitiated = handleAction(
  USER_SELF_RETRIEVE_INITIATED,
  (state: State) => {
    return {
      ...state,
      retrievingSelf: true,
    };
  },
);

const selfRetrieveSuccess = handleAction(
  USER_SELF_RETRIEVE_SUCCESS,
  (state: State, action: UserSelfRetrieveSuccessAction) => {
    return {
      ...state,
      retrievingSelf: false,
      self: action.payload,
    };
  },
);

export default flattenActions(
  initialState,
  selfRetrieveInitiated,
  selfRetrieveSuccess,
);
