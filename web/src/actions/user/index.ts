import {
  UserSelfRetrieveInitiatedAction,
  UserSelfRetrieveSuccessAction,
} from './get_user_self_action';
import { UserUpdateNetworksAction } from './update_networks_action';
import { UserUpdateAction } from './update_user';
import { UserUpdatePrefsAction } from './update_user_preferences';
import {
  UserCreateListAction,
  UserCreateListSuccessAction,
  UserDeleteListAction,
  UserUpdateListAction,
} from '../lists';
import {
  UserUpdateItemTagsAction,
  UserUpdateItemTagsSuccessAction,
} from './update_user_tags';

export * from './get_user_self_action';
export * from './remove_user_tag';
export * from './update_networks_action';
export * from './update_user';
export * from './update_user_preferences';
export * from './update_user_tags';

export type UserActionTypes =
  | UserSelfRetrieveInitiatedAction
  | UserSelfRetrieveSuccessAction
  | UserUpdateNetworksAction
  | UserUpdateAction
  | UserUpdatePrefsAction
  | UserCreateListAction
  | UserCreateListSuccessAction
  | UserUpdateItemTagsAction
  | UserUpdateItemTagsSuccessAction
  | UserDeleteListAction
  | UserUpdateListAction;
