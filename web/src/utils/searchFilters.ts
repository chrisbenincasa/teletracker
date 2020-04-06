import { ItemType, NetworkType, OpenRange, SortOptions } from '../types';
import _ from 'lodash';

export interface SlidersState {
  releaseYear?: OpenRange;
  imdbRating?: OpenRange;
}

export const DEFAULT_FILTER_PARAMS: FilterParams = {};

function removeUndefinedKeys(obj: FilterParams): FilterParams {
  return _.pickBy(obj, _.negate(_.isUndefined));
}

export const isDefaultFilter = (filters: FilterParams): boolean => {
  return _.isEqual(
    removeUndefinedKeys(filters),
    removeUndefinedKeys(DEFAULT_FILTER_PARAMS),
  );
};

export interface FilterParams {
  genresFilter?: number[];
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  sortOrder?: SortOptions;
  sliders?: SlidersState;
  people?: string[];
}
