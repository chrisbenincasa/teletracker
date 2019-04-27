import { ITEM_FETCH_INITIATED, ITEM_FETCH_SUCCESSFUL, ITEM_FETCH_FAILED } from '../constants/item-detail';
import { Dispatch } from 'redux';
import { TeletrackerApi } from '../utils/api-client';
import { Thing } from '../types/external/themoviedb/Movie';
import { createAction } from 'redux-actions';

interface ItemFetchInitiatedAction {
  type: typeof ITEM_FETCH_INITIATED;
  id: number;
}

interface ItemFetchSuccessfulAction {
  type: typeof ITEM_FETCH_SUCCESSFUL;
  item: Thing;
}

interface ItemFetchFailedAction {
  type: typeof ITEM_FETCH_FAILED;
  item: Thing;
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

const ItemFetchFailed = createAction(ITEM_FETCH_FAILED);

export type ItemDetailActionTypes =
| ItemFetchInitiatedAction
| ItemFetchSuccessfulAction
| ItemFetchFailedAction;

const client = TeletrackerApi.instance;

export const fetchItemDetails = (id: number) => {
  return async (dispatch: Dispatch) => {
    dispatch(itemFetchInitiated(id));

    // To do fix for shows and such
    // just testing for now
    return client
      .getMovie(id)
      .then(response => {
        if (response.ok) {
          console.log(response.data);
          dispatch(itemFetchSuccess(response.data));
        } else {
          dispatch(ItemFetchFailed());
        }
      })
      .catch(e => {
        console.error(e);

        dispatch(ItemFetchFailed(e));
      });
  };
};
