import _ from 'lodash';

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
