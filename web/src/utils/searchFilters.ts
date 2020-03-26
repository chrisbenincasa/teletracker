import { ItemType, NetworkType, SortOptions } from '../types';
import _ from 'lodash';

export interface SliderParamState {
  min?: number;
  max?: number;
}

export interface SlidersState {
  releaseYear?: SliderParamState;
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
