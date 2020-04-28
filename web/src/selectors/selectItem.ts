import { AppState } from '../reducers';
import { Id } from '../types/v2';
import createDeepEqSelector from '../hooks/createDeepEqSelector';

export default createDeepEqSelector(
  (state: AppState) => state.itemDetail.thingsById,
  (_, itemId: Id) => itemId,
  (itemsById, itemId) => {
    return itemsById[itemId];
  },
);
