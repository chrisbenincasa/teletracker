import { ITEM_FETCH_INITIATED, ITEM_FETCH_SUCCESSFUL } from '../constants/item-detail';
import { Dispatch } from 'redux';
import { TeletrackerApi } from '../utils/api-client';
import { Thing } from '../types/external/themoviedb/Movie';

interface ItemFetchInitiatedAction {
  type: typeof ITEM_FETCH_INITIATED;
  id: number;
}

interface ItemFetchSuccessfulAction {
  type: typeof ITEM_FETCH_SUCCESSFUL;
  item: any;
}

export const itemFetchInitiated: (
  id: number,
) => ItemFetchInitiatedAction = id => ({
  type: ITEM_FETCH_INITIATED,
  id,
});

export const itemFetchSuccess: (
  item: Thing,
) => ItemFetchSuccessfulAction = item => ({
  type: ITEM_FETCH_SUCCESSFUL,
  item,
});

export type ItemDetailActionTypes = ItemFetchInitiatedAction | ItemFetchSuccessfulAction;

const client = TeletrackerApi.instance;

export const fetchItemDetails = (id: number) => {
  return async (dispatch: Dispatch) => {
    dispatch(itemFetchInitiated(id));

    // To do fix for shows and such
    // just testing for now
    return client.getMovie(id).then(response => {
      if (response.ok) {
        console.log(response.data);
        dispatch(itemFetchSuccess(response.data));
      }
    });
  };
};
