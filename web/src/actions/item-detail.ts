import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
  ITEM_FETCH_FAILED,
} from '../constants/item-detail';
import { FSA, ErrorFluxStandardAction } from 'flux-standard-action';
import { Dispatch } from 'redux';
import { TeletrackerApi } from '../utils/api-client';
import { Thing } from '../types';
import { createAction } from './utils';

export type ItemFetchInitiatedAction = FSA<typeof ITEM_FETCH_INITIATED, number>;

export type ItemFetchSuccessfulAction = FSA<
  typeof ITEM_FETCH_SUCCESSFUL,
  Thing
>;

export type ItemFetchFailedAction = ErrorFluxStandardAction<
  typeof ITEM_FETCH_FAILED,
  Error
>;

export const itemFetchInitiated = createAction<ItemFetchInitiatedAction>(
  ITEM_FETCH_INITIATED,
);

export const itemFetchSuccess = createAction<ItemFetchSuccessfulAction>(
  ITEM_FETCH_SUCCESSFUL,
);

const ItemFetchFailed = createAction<ItemFetchFailedAction>(ITEM_FETCH_FAILED);

export type ItemDetailActionTypes =
  | ItemFetchInitiatedAction
  | ItemFetchSuccessfulAction
  | ItemFetchFailedAction;

const client = TeletrackerApi.instance;

export const fetchItemDetails = (id: number, type: string) => {
  return async (dispatch: Dispatch) => {
    dispatch(itemFetchInitiated(id));

    // To do fix for shows and such
    // just testing for now
    return client
      .getItem(id, type)
      .then(response => {
        if (response.ok) {
          dispatch(itemFetchSuccess(response.data.data));
        } else {
          dispatch(ItemFetchFailed(new Error()));
        }
      })
      .catch(e => {
        console.error(e);

        dispatch(ItemFetchFailed(e));
      });
  };
};
