import { ItemType, NetworkType, OpenRange, SortOptions } from '../types';
import _ from 'lodash';

export interface SlidersState {
  releaseYear?: OpenRange;
  imdbRating?: OpenRange;
}

export type SliderChange = Partial<SlidersState>;

export const DEFAULT_FILTER_PARAMS: FilterParams = {};

export function removeUndefinedKeys<T extends object>(obj: T): Partial<T> {
  return _.pickBy(obj, _.negate(_.isUndefined));
}

export const isDefaultFilter = (filters: FilterParams): boolean => {
  return _.isEqual(
    normalizeFilterParams(filters),
    normalizeFilterParams(DEFAULT_FILTER_PARAMS),
  );
};

export function isObjectEmpty<T extends object>(obj: T): boolean {
  return !_(obj)
    .values()
    .some(_.negate(_.isUndefined));
}

export function normalizeFilterParams(
  filters: Readonly<FilterParams>,
): FilterParams {
  let copy: FilterParams = removeUndefinedKeys({ ...filters });

  if (copy.genresFilter && copy.genresFilter.length === 0) {
    copy = _.omit(copy, 'genresFilter');
  }

  if (copy.itemTypes && copy.itemTypes.length === 0) {
    copy = _.omit(copy, 'itemTypes');
  }

  if (copy.networks && copy.networks.length === 0) {
    copy = _.omit(copy, 'networks');
  }

  if (copy.people && copy.people.length === 0) {
    copy = _.omit(copy, 'people');
  }

  if (copy.sliders) {
    copy.sliders = removeUndefinedKeys(copy.sliders);

    if (copy.sliders.imdbRating && isObjectEmpty(copy.sliders.imdbRating)) {
      copy.sliders = _.omit(copy.sliders, 'imdbRating');
    }

    if (copy.sliders.releaseYear && isObjectEmpty(copy.sliders.releaseYear)) {
      copy.sliders = _.omit(copy.sliders, 'releaseYear');
    }

    if (isObjectEmpty(copy.sliders)) {
      copy = _.omit(copy, 'sliders');
    }
  }

  return copy;
}

export interface FilterParams {
  genresFilter?: number[];
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  sortOrder?: SortOptions;
  sliders?: SlidersState;
  people?: string[];
}
