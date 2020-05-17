import { FSA } from 'flux-standard-action';
import { withPayloadType } from '../utils';
import { createAction } from '@reduxjs/toolkit';

export * from './login_action';
export * from './logout_action';
export * from './signup_action';
export * from './watch_auth_state';
export * from './set_token_action';
export * from './auth_with_google';

export const USER_STATE_CHANGE = 'auth/USER_STATE_CHANGE';

export interface UserStateChangePayload {
  readonly authenticated: boolean;
  readonly userId?: string;
}

export type UserStateChangeAction = FSA<
  typeof USER_STATE_CHANGE,
  UserStateChangePayload
>;
export const userStateChange = createAction(
  USER_STATE_CHANGE,
  withPayloadType<UserStateChangePayload>(),
);
