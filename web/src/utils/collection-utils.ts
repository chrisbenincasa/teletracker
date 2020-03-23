import _ from 'lodash';
import * as R from 'ramda';
import { HasSlug } from '../types/v2/Item';

// This is basically a flatMap for list => option
export function collect<T, U>(
  collection: T[],
  mapper: (item: T) => U | undefined,
): U[] {
  return _.chain(collection)
    .map(mapper)
    .filter(u => !_.isUndefined(u))
    .map(u => u!)
    .value();
}

export const headOption = R.ifElse(R.isNil, R.always(undefined), R.head);

type KeyedMapT<T> = { [key: string]: T };

/**
 * Given an id and a map of id->value, attempt to extract the value. Falls back
 * to using the slug value from T and iterating over the map
 * @param id
 * @param initialValue
 * @param itemsById
 */
export function extractValue<T extends HasSlug>(
  id: string,
  initialValue: T | undefined,
  itemsById: KeyedMapT<T>,
): T | undefined {
  return initialValue || itemsById[id] || findValueBySlug(id, itemsById);
}

export function findValueBySlug<T extends HasSlug>(
  slug: string,
  itemsById: KeyedMapT<T>,
): T | undefined {
  return _.find(_.values(itemsById), item => item.slug === slug);
}
