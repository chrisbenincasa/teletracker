import React, { createContext, ReactNode, useCallback, useEffect } from 'react';
import {
  DEFAULT_FILTER_PARAMS,
  FilterParams,
  makeFilterParams,
  normalizeFilterParams,
} from '../../utils/searchFilters';
import { useStateDeepEq } from '../../hooks/useStateDeepEq';
import { filterParamsEqual } from '../../utils/changeDetection';
import useCustomCompareMemo from '../../hooks/useMemoCompare';
import dequal from 'dequal';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForNextRouter,
} from '../../utils/urlHelper';
import qs from 'querystring';
import { useRouterDeep } from '../../hooks/useRouterDeep';
import { hookDeepEqual } from '../../hooks/util';

export interface FilterContextState {
  filters: FilterParams;
  setFilters: (newFilters: FilterParams) => void;
  defaultFilters?: FilterParams;
}

export const FilterContext = createContext<FilterContextState>({
  filters: makeFilterParams(),
  setFilters: () => {},
});

interface WithItemFiltersProps {
  defaultFilters?: FilterParams;
  children: ReactNode;
}

function withFilters(
  initialFilters: FilterParams,
  defaultFilters?: FilterParams,
): FilterContextState {
  const [filters, setFilters] = useStateDeepEq(
    initialFilters,
    (left, right) => {
      return filterParamsEqual(left, right, defaultFilters?.sortOrder);
    },
  );

  const actuallySetFilters = useCallback(
    (newFilters: FilterParams) => {
      setFilters(normalizeFilterParams(newFilters));
    },
    [filters],
  );

  return {
    filters,
    setFilters: actuallySetFilters,
  };
}

function WithItemFilters(props: WithItemFiltersProps) {
  const router = useRouterDeep();
  const stringifiedQuery = qs.stringify(router.query);
  const paramsFromQuery = parseFilterParamsFromQs(stringifiedQuery);
  const initialFilters = {
    ...(props.defaultFilters || DEFAULT_FILTER_PARAMS),
    ...paramsFromQuery,
  };

  const filterState = withFilters(initialFilters, props.defaultFilters);
  const memoedFilterState = useCustomCompareMemo(
    () => ({ ...filterState, defaultFilters: props.defaultFilters }),
    [filterState.filters],
    hookDeepEqual,
  );

  useEffect(() => {
    updateUrlParamsForNextRouter(
      router,
      memoedFilterState.filters,
      [],
      memoedFilterState.defaultFilters,
    );
  }, [memoedFilterState]);

  return (
    <FilterContext.Provider value={memoedFilterState}>
      {props.children}
    </FilterContext.Provider>
  );
}

// WithItemFilters.whyDidYouRender = true;

export default React.memo(WithItemFilters, dequal);
