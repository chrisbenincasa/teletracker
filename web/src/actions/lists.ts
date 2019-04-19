import { Dispatch } from 'redux';
import {
  LIST_ADD_ITEM_FAILED,
  LIST_ADD_ITEM_INITIATED,
  LIST_ADD_ITEM_SUCCESS,
  LIST_RETRIEVE_INITIATED,
  LIST_RETRIEVE_SUCCESS,
  LIST_RETRIEVE_FAILED,
} from '../constants/lists';
import { TeletrackerApi } from '../utils/api-client';
import { retrieveUser } from './user';
import { AppState } from '../reducers';
import { createAction } from 'redux-actions';
import { List } from '../types';

interface ListAddInitiatedAction {
  type: typeof LIST_ADD_ITEM_INITIATED;
  listId: string;
  itemId: string;
}

const listAdd = (listId: string, itemId: string) => {
  return {
    type: LIST_ADD_ITEM_INITIATED,
    listId,
    itemId,
  };
};

interface ListAddSuccessAction {
  type: typeof LIST_ADD_ITEM_SUCCESS;
}

interface ListAddFailedAction {
  type: typeof LIST_ADD_ITEM_FAILED;
}

interface ListRetrieveInitiatedAction {
  type: typeof LIST_RETRIEVE_INITIATED;
  payload: string | number;
}

const ListRetrieveInitiated = createAction(
  LIST_RETRIEVE_INITIATED,
  (listId: string | number): ListRetrieveInitiatedAction => ({
    type: LIST_RETRIEVE_INITIATED,
    payload: listId,
  }),
);

interface ListRetrieveSuccessAction {
  type: typeof LIST_RETRIEVE_SUCCESS;
  payload: List;
}

export const ListRetrieveSuccess = createAction(LIST_RETRIEVE_SUCCESS);

interface ListRetrieveFailedAction {
  type: typeof LIST_RETRIEVE_SUCCESS;
  payload: Error;
}

const ListRetrieveFailed = createAction(LIST_RETRIEVE_FAILED);

type ListAddActions =
  | ListAddInitiatedAction
  | ListAddSuccessAction
  | ListAddFailedAction
  | ListRetrieveInitiatedAction
  | ListRetrieveSuccessAction
  | ListRetrieveFailedAction;

export type ListActions = ListAddActions;

const client = TeletrackerApi.instance;

export const addToList = (listId: string, itemId: string) => {
  return (dispatch: Dispatch, stateFn: () => AppState) => {
    dispatch(listAdd(listId, itemId));

    return client
      .addItemToList(listId, itemId)
      .then(response => {
        if (response.ok) {
          dispatch(listAddSuccess());
          retrieveUser(true)(dispatch, stateFn);
        } else {
          dispatch(listAddFailure());
        }
      })
      .catch(err => {
        dispatch(listAddFailure());
      });
  };

  function listAddSuccess() {
    return { type: LIST_ADD_ITEM_SUCCESS };
  }

  function listAddFailure() {
    return { type: LIST_ADD_ITEM_FAILED };
  }
};

export const retrieveList = (listId: string, force: boolean = false) => {
  return (dispatch: Dispatch, stateFn: () => AppState) => {
    dispatch(ListRetrieveInitiated(listId));

    let state = stateFn();

    return client
      .getList(listId)
      .then(response => {
        if (response.ok && response.data) {
          dispatch(ListRetrieveSuccess(response.data.data));
        } else {
          dispatch(ListRetrieveFailed());
        }
      })
      .catch(e => {
        console.error(e);

        dispatch(ListRetrieveFailed(e));
      });
  };
};
