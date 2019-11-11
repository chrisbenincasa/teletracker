import _ from 'lodash';
import { FilterParams } from './searchFilters';

export function filterParamsEqual(left: FilterParams, right: FilterParams) {
  if (
    _.isUndefined(left.genresFilter) !== _.isUndefined(right.genresFilter) ||
    _.isUndefined(left.itemTypes) !== _.isUndefined(right.itemTypes) ||
    _.isUndefined(left.networks) !== _.isUndefined(right.networks) ||
    _.isUndefined(left.sortOrder) !== _.isUndefined(right.sortOrder)
  ) {
    return false;
  }

  if (
    left.genresFilter &&
    right.genresFilter &&
    _.xor(left.genresFilter, right.genresFilter).length !== 0
  ) {
    return false;
  }

  if (
    left.itemTypes &&
    right.itemTypes &&
    _.xor(left.itemTypes, right.itemTypes).length !== 0
  ) {
    return false;
  }

  if (
    left.networks &&
    right.networks &&
    _.xor(left.networks, right.networks).length !== 0
  ) {
    return false;
  }

  if (left.sortOrder !== right.sortOrder) {
    return false;
  }

  return true;
}
