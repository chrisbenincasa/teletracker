import { FSA } from 'flux-standard-action';
import { CognitoUser } from '@aws-amplify/auth';
import { createAction } from '../utils';

export * from './login_action';
export * from './logout_action';
export * from './signup_action';
export * from './watch_auth_state';
export * from './set_token_action';
export * from './auth_with_google';

export const USER_STATE_CHANGE = 'auth/USER_STATE_CHANGE';
export type UserStateChangeAction = FSA<
  typeof USER_STATE_CHANGE,
  CognitoUser | undefined
>;
export const UserStateChange = createAction<UserStateChangeAction>(
  USER_STATE_CHANGE,
);
