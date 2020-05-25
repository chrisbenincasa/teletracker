import { createSelector } from 'reselect';
import { AppState } from '../reducers';
import { List } from '../types';
import _ from 'lodash';

export default createSelector(
  (state: AppState) => state.lists.listsById,
  (_, listId) => listId,
  (listsById, listId) => {
    let currentList: List | undefined = listsById[listId];
    if (!currentList) {
      currentList = _.find(listsById, list =>
        (list.aliases || []).includes(listId),
      );
    }
    return currentList;
  },
);
