import { Item } from '../types/v2/Item';
import useStateSelector from './useStateSelector';
import { extractValue } from '../utils/collection-utils';

// TODO: We need this to take an itemType to be totally correct
export default function useItem(
  itemId: string,
  initialItem?: Item,
): Item | undefined {
  const itemsById = useStateSelector(state => state.itemDetail.thingsById);

  return extractValue(itemId, initialItem, itemsById);
}
