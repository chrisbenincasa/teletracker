import useStateSelector, { AppStateSelector } from './useStateSelector';
import { FilterParams } from '../utils/searchFilters';
import { useContext, useEffect } from 'react';
import { FilterContext } from '../components/Filters/FilterContext';
import { filterParamsEqual } from '../utils/changeDetection';

export default function useFilterLoadEffect(
  effectCb: (...args: any) => void,
  filterStateSelector: AppStateSelector<FilterParams | undefined>,
) {
  const { filters, defaultFilters } = useContext(FilterContext);
  const stateFilters = useStateSelector(filterStateSelector);

  useEffect(() => {
    if (!filterParamsEqual(filters, stateFilters, defaultFilters?.sortOrder)) {
      console.log('loading bc of filters', filters, stateFilters);
      effectCb();
    }
  }, [filters]);
}
