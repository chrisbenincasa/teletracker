import _ from 'lodash';

export function optionalSetsEqual<T>(
  l: T[] | undefined,
  r: T[] | undefined,
): boolean {
  if (l && r) {
    return setsEqual(l, r);
  } else {
    return false;
  }
}

export function setsEqual<T>(l: T[], r: T[]): boolean {
  return _.xor(l, r).length === 0;
}
