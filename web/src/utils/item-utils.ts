import { ThingMap } from '../reducers/item-detail';
import { Item } from '../types/v2/Item';
import _ from 'lodash';

export function windowTitleForItem(
  givenId: string,
  givenItem: Item | undefined,
  itemsById: ThingMap,
  itemsBySlug: ThingMap,
): string {
  let titleToShow: string | undefined;

  if (givenItem) {
    titleToShow =
      givenItem.title && givenItem.title.length
        ? givenItem.title[0]
        : givenItem.original_title;
  } else if (itemsById[givenId]) {
    titleToShow =
      itemsById[givenId].title && itemsById[givenId].title!.length
        ? itemsById[givenId].title![0]
        : itemsById[givenId].original_title;
  } else if (itemsBySlug[givenId]) {
    titleToShow =
      itemsBySlug[givenId].title && itemsBySlug[givenId].title!.length
        ? itemsBySlug[givenId].title![0]
        : itemsBySlug[givenId].original_title;
  }

  if (_.isUndefined(titleToShow)) {
    titleToShow = 'Not Found';
  } else {
    titleToShow = `${titleToShow} | Where to stream, rent, or buy`;
  }

  return titleToShow;
}
