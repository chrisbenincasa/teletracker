import { FilterParams } from '../utils/searchFilters';
import { flattenActions, handleAction } from './utils';
import { FilterUpdateAction, UPDATE_FILTERS } from '../actions/filters';

export interface State {
  currentFilters?: FilterParams;
}

const initialState: State = {};

const handleFiltersChanged = handleAction<FilterUpdateAction, State>(
  UPDATE_FILTERS,
  (state, action) => {
    return state;
  },
);

export default flattenActions('filters', initialState, handleFiltersChanged);
