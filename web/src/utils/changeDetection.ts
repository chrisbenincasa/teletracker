import _ from 'lodash';
import {
  FilterParams,
  normalizeFilterParams,
  selectableNetworksEqual,
  SlidersState,
} from './searchFilters';
import { OpenRange, SortOptions } from '../types';
import { setsEqual } from './sets';

export function filterParamsEqual(
  leftParam: FilterParams | undefined,
  rightParam: FilterParams | undefined,
  defaultSortOrder?: SortOptions,
) {
  const left = leftParam ? normalizeFilterParams(leftParam) : {};
  const right = rightParam ? normalizeFilterParams(rightParam) : {};

  if (left && right) {
    if (
      _.isUndefined(left.genresFilter) !== _.isUndefined(right.genresFilter) ||
      _.isUndefined(left.itemTypes) !== _.isUndefined(right.itemTypes) ||
      _.isUndefined(left.networks) !== _.isUndefined(right.networks) ||
      _.isUndefined(left.sortOrder) !== _.isUndefined(right.sortOrder) ||
      _.isUndefined(left.sliders) !== _.isUndefined(right.sliders) ||
      _.isUndefined(left.people) !== _.isUndefined(right.people) ||
      _.isUndefined(left.offers) !== _.isUndefined(right.offers)
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

    if (!selectableNetworksEqual(left.networks, right.networks)) {
      return false;
    }

    if (left.sortOrder !== right.sortOrder) {
      const isEquivDefault =
        (_.isUndefined(left.sortOrder) &&
          right.sortOrder === defaultSortOrder) ||
        (_.isUndefined(right.sortOrder) && left.sortOrder === defaultSortOrder);
      if (!defaultSortOrder || !isEquivDefault) {
        return false;
      }
    }

    if (left.offers && right.offers) {
      return setsEqual(left.offers.types || [], right.offers.types || []);
    }

    if (sliderChanged(left.sliders?.releaseYear, right.sliders?.releaseYear)) {
      return false;
    }

    if (sliderChanged(left.sliders?.imdbRating, right.sliders?.imdbRating)) {
      return false;
    }

    if (
      left.people &&
      right.people &&
      _.xor(left.people, right.people).length !== 0
    ) {
      return false;
    }

    return true;
  } else if ((!left && right) || (left && !right)) {
    return false;
  } else {
    return true;
  }
}

function sliderChanged(left?: OpenRange, right?: OpenRange) {
  if ((!left && right) || (left && !right)) {
    return true;
  } else if (left && right) {
    return left.min !== right.min || left.max !== right.max;
  } else {
    return false;
  }
}
