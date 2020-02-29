import { Item } from '../types/v2/Item';
import useStateSelector from './useStateSelector';

// TODO: We need this to take an itemType to be totally correct
export default function useItem(itemId: string, initialItem?: Item) {
  const itemsById = useStateSelector(state => state.itemDetail.thingsById);
  const itemsBySlug = useStateSelector(state => state.itemDetail.thingsBySlug);

  return initialItem || itemsById[itemId] || itemsBySlug[itemId];
}
