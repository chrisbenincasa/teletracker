import { ItemType, NetworkType, SortOptions } from '../types';
import _ from 'lodash';

export interface SliderParamState {
  min?: number;
  max?: number;
}

export interface SlidersState {
  releaseYear?: SliderParamState;
}

export const DEFAULT_FILTER_PARAMS: FilterParams = {
  sortOrder: 'default',
};

export const isDefaultFilter = (filters: FilterParams): boolean => {
  return _.isEqual(filters, DEFAULT_FILTER_PARAMS);
};

export interface FilterParams {
  genresFilter?: number[];
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  sortOrder: SortOptions;
  sliders?: SlidersState;
  people?: string[];
}
