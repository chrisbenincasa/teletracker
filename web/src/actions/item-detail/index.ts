import {
  ItemFetchFailedAction,
  ItemFetchInitiatedAction,
  ItemFetchSuccessfulAction,
} from './get_item_details';

export * from './get_item_details';
export * from './get_items_batch';

export const ACTION_WATCHED = 'Watched';
export const ACTION_ENJOYED = 'Enjoyed';

export type ItemDetailActionTypes =
  | ItemFetchInitiatedAction
  | ItemFetchSuccessfulAction
  | ItemFetchFailedAction;
