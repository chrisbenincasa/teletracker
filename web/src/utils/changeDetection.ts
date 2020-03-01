import _ from 'lodash';
import { FilterParams, SliderParamState } from './searchFilters';
import { SortOptions } from '../types';

export function filterParamsEqual(
  left: FilterParams | undefined,
  right: FilterParams | undefined,
  defaultSortOrder?: SortOptions,
) {
  if (left && right) {
    if (
      _.isUndefined(left.genresFilter) !== _.isUndefined(right.genresFilter) ||
      _.isUndefined(left.itemTypes) !== _.isUndefined(right.itemTypes) ||
      _.isUndefined(left.networks) !== _.isUndefined(right.networks) ||
      _.isUndefined(left.sortOrder) !== _.isUndefined(right.sortOrder) ||
      _.isUndefined(left.sliders) !== _.isUndefined(right.sliders) ||
      _.isUndefined(left.people) !== _.isUndefined(right.people)
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
      if (
        !defaultSortOrder ||
        (defaultSortOrder &&
          left.sortOrder === 'default' &&
          right.sortOrder !== defaultSortOrder) ||
        (left.sortOrder !== defaultSortOrder && right.sortOrder === 'default')
      ) {
        return false;
      }
    }

    if (left.sliders && right.sliders) {
      if (sliderChanged(left.sliders.releaseYear, right.sliders.releaseYear)) {
        return false;
      }
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

function sliderChanged(left?: SliderParamState, right?: SliderParamState) {
  if ((!left && right) || (left && !right)) {
    return true;
  } else if (left && right) {
    return left.min !== right.min || left.max !== right.max;
  } else {
    return false;
  }
}
