import createDeepEqSelector from '../hooks/createDeepEqSelector';
import { AppState } from '../reducers';
import { Id } from '../types/v2';
import _ from 'lodash';

export default createDeepEqSelector(
  (state: AppState) => state.itemDetail.thingsById,
  (_, itemIds: Id[]) => itemIds,
  (itemsById, itemIds) => {
    return _.compact(_.map(itemIds, id => itemsById[id]));
  },
);