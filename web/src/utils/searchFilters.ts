import { ItemType, NetworkType, OpenRange, SortOptions } from '../types';
import _ from 'lodash';
import { List, Record, RecordOf } from 'immutable';

export type SlidersStateType = {
  releaseYear?: OpenRange;
  imdbRating?: OpenRange;
};

export type SlidersState = RecordOf<SlidersStateType>;

export type SliderChange = Partial<SlidersState>;

export type FilterParamsType = {
  genresFilter?: List<number>;
  itemTypes?: List<ItemType>;
  networks?: List<NetworkType>;
  sortOrder?: SortOptions;
  sliders?: SlidersState;
  people?: List<string>;
};

export type FilterParams = RecordOf<FilterParamsType>;

export const DEFAULT_FILTER_PARAMS: FilterParamsType = {};

export const makeFilterParams = Record(DEFAULT_FILTER_PARAMS);

export function removeUndefinedKeys<T extends object>(obj: T): Partial<T> {
  return _.pickBy(obj, _.negate(_.isUndefined));
}

export function removeUndefinedRecordKeys<T extends RecordOf<any>>(
  record: T,
): T {
  return record.withMutations(mutable => {
    for (let key of mutable.toSeq().keys()) {
      if (_.isUndefined(mutable.get(key))) {
        mutable.remove(key);
      }
    }
  });
}

export const isDefaultFilter = (filters: FilterParams): boolean => {
  return _.isEqual(
    normalizeFilterParams(filters),
    normalizeFilterParams(makeFilterParams()),
  );
};

export function isObjectEmpty<T extends object>(obj: T): boolean {
  return !_(obj)
    .values()
    .some(_.negate(_.isUndefined));
}

export function isRecordEmpty<T extends RecordOf<any>>(record: T): boolean {
  for (let value in record.toSeq().values()) {
    if (!_.isUndefined(value)) {
      return false;
    }
  }

  return true;
}

export function normalizeFilterParams(filters: FilterParams): FilterParams {
  return removeUndefinedRecordKeys(filters).withMutations(copy => {
    if (copy.genresFilter && copy.genresFilter.size === 0) {
      copy.remove('genresFilter');
    }

    if (copy.itemTypes && copy.itemTypes.size === 0) {
      copy.remove('itemTypes');
    }

    if (copy.networks && copy.networks.size === 0) {
      copy.remove('networks');
    }

    if (copy.people && copy.people.size === 0) {
      copy.remove('people');
    }

    if (copy.sliders) {
      const newSliders = copy.sliders.withMutations(slidersCopy => {
        const slidersCleaned = removeUndefinedRecordKeys(slidersCopy);
        if (
          slidersCleaned.imdbRating &&
          isObjectEmpty(slidersCleaned.imdbRating)
        ) {
          slidersCleaned.remove('imdbRating');
        }

        if (
          slidersCleaned.releaseYear &&
          isObjectEmpty(slidersCleaned.releaseYear)
        ) {
          slidersCleaned.remove('releaseYear');
        }
      });

      if (isRecordEmpty(newSliders)) {
        copy.remove('sliders');
      } else {
        copy.set('sliders', newSliders);
      }
    }
  });
}
