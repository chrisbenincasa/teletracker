import { Item } from '../types/v2/Item';
import useStateSelector from './useStateSelector';
import { ThingMap } from '../reducers/item-detail';
import _ from 'lodash';
import { findByItemMatchingSlug } from '../utils/item-utils';

// TODO: We need this to take an itemType to be totally correct
export default function useItem(
  itemId: string,
  initialItem?: Item,
): Item | undefined {
  const itemsById = useStateSelector(state => state.itemDetail.thingsById);

  return (
    initialItem ||
    itemsById[itemId] ||
    findByItemMatchingSlug(itemId, itemsById)
  );
}
