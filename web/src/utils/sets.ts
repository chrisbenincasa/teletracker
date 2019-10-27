import _ from 'lodash';

export function setsEqual<T>(l: T[], r: T[]): boolean {
  return _.xor(l, r).length === 0;
}
