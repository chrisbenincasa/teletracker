import { ItemType, NetworkType, OpenRange, SortOptions } from '../types';
import _ from 'lodash';
import produce from 'immer';

export interface SlidersState {
  readonly releaseYear?: OpenRange;
  readonly imdbRating?: OpenRange;
}

export interface FilterParams {
  readonly genresFilter?: number[];
  readonly itemTypes?: ItemType[];
  readonly networks?: NetworkType[];
  readonly sortOrder?: SortOptions;
  readonly sliders?: SlidersState;
  readonly people?: string[];
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
  return produce(removeUndefinedKeys(filters), draft => {
    if (draft.genresFilter && draft.genresFilter.length === 0) {
      delete draft.genresFilter;
    }

    if (draft.itemTypes && draft.itemTypes.length === 0) {
      delete draft.itemTypes;
    }

    if (draft.networks && draft.networks.length === 0) {
      delete draft.networks;
    }

    if (draft.people && draft.people.length === 0) {
      delete draft.people;
      draft = _.omit(draft, 'people');
    }

    if (draft.sliders) {
      const newSliders = produce(
        removeUndefinedKeys(draft.sliders),
        slidersDraft => {
          if (
            slidersDraft.imdbRating &&
            isObjectEmpty(slidersDraft.imdbRating)
          ) {
            delete slidersDraft.imdbRating;
          }

          if (
            slidersDraft.releaseYear &&
            isObjectEmpty(slidersDraft.releaseYear)
          ) {
            delete slidersDraft.releaseYear;
          }
        },
      );

      if (isObjectEmpty(newSliders)) {
        delete draft.sliders;
      }
    }
  });
}
