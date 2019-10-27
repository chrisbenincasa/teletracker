import { ItemType, ListSortOptions, NetworkType } from '../types';

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

export interface FilterParams {
  genresFilter?: number[];
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  sortOrder: ListSortOptions;
  sliders?: SlidersState;
}
