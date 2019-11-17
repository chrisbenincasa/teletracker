import _ from 'lodash';
import * as R from 'ramda';

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
