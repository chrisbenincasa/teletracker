import createDeepEqSelector from '../hooks/createDeepEqSelector';
import { AppState } from '../reducers';
import _ from 'lodash';
import { ListsByIdMap } from '../reducers/lists';

export default createDeepEqSelector(
  (state: AppState) => state.lists.listsById,
  (loadedLists: ListsByIdMap) => {
    return _.filter(loadedLists, value => value.ownedByRequester);
  },
);
