import { ThingMap } from '../reducers/item-detail';
import { Item } from '../types/v2/Item';
import _ from 'lodash';

export function windowTitleForItem(
  givenId: string,
  givenItem: Item | undefined,
  itemsById: ThingMap,
): string {
  let item = extractItem(givenId, givenItem, itemsById);

  let titleToShow: string | undefined;

  if (item) {
    titleToShow = item.canonicalTitle;
  }

  if (_.isUndefined(titleToShow)) {
    titleToShow = 'Not Found';
  } else {
    titleToShow = `${titleToShow} | Where to stream, rent, or buy`;
  }

  return titleToShow;
}

export function extractItem(
  itemId: string,
  initialItem: Item | undefined,
  itemsById: ThingMap,
): Item | undefined {
  return (
    initialItem ||
    itemsById[itemId] ||
    findByItemMatchingSlug(itemId, itemsById)
  );
}

export function findByItemMatchingSlug(
  slug: string,
  itemsById: ThingMap,
): Item | undefined {
  return _.find(_.values(itemsById), item => item.slug === slug);
}
