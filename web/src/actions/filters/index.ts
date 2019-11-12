import { FSA } from 'flux-standard-action';
import { takeEvery, select } from '@redux-saga/core/effects';
import { FilterParams } from '../../utils/searchFilters';
import { createAction } from '../utils';
import { State as FilterState } from '../../reducers/filters';
import { AppState } from '../../reducers';

export const UPDATE_FILTERS = 'filters/UPDATE';
export const FILTERS_CHANGED = 'filters/CHANGED';

export type FilterUpdateAction = FSA<
  typeof UPDATE_FILTERS,
  Partial<FilterParams>
>;

export type FiltersChangedAction = FSA<typeof FILTERS_CHANGED, FilterParams>;

export const filtersChanged = createAction<FiltersChangedAction>(
  FILTERS_CHANGED,
);

export const filtersChangedSaga = function*() {
  yield takeEvery(UPDATE_FILTERS, function*({ payload }: FilterUpdateAction) {
    if (payload) {
      let currentFilters: FilterState = yield select(
        (state: AppState) => state.filters,
      );

      console.log(currentFilters, payload);
    }
  });
};
