import {
  ITEM_FETCH_INITIATED,
  ITEM_FETCH_SUCCESSFUL,
  ITEM_FETCH_FAILED
} from '../constants/item-detail';
import { ItemDetailActionTypes } from '../actions/item-detail';
import { Thing } from "../types";

export interface State {
  fetching: boolean;
  currentId: number;
  itemDetail?: Thing;
}

const initialState: State = {
  fetching: false,
  currentId: 70,
};

export default function itemDetailsReducer(
  state: State = initialState,
  action: ItemDetailActionTypes,
): State {
  switch (action.type) {
    case ITEM_FETCH_INITIATED:
      return {
        ...state,
        fetching: true,
        currentId: action.id,
      };

    case ITEM_FETCH_SUCCESSFUL:
      return {
        ...state,
        fetching: false,
        itemDetail: action.item,
      };
    default:
      return state;
  }
}
