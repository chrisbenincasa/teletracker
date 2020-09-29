import React, { createContext, ReactNode, useCallback, useEffect } from 'react';
import {
  DEFAULT_FILTER_PARAMS,
  FilterParams,
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
import { hookDeepEqual } from '../../hooks/util';
import useEffectCompare from '../../hooks/useEffectCompare';
import { useRouter } from 'next/router';

export interface FilterContextState {
  readonly filters: FilterParams;
  readonly setFilters: (newFilters: FilterParams) => void;
  readonly clearFilters: () => void;
  readonly defaultFilters?: FilterParams;
  readonly currentFiltersAreDefault: boolean;
}

export const FilterContext = createContext<FilterContextState>({
  filters: DEFAULT_FILTER_PARAMS,
  setFilters: () => {},
  clearFilters: () => {},
  currentFiltersAreDefault: true,
});

interface WithItemFiltersProps {
  readonly initialFilters?: FilterParams;
  readonly children: ReactNode;
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

  const clearFilters = useCallback(() => {
    actuallySetFilters(defaultFilters || {});
  }, []);
  return {
    filters,
    setFilters: actuallySetFilters,
    clearFilters,
    currentFiltersAreDefault: filterParamsEqual(filters, defaultFilters),
  };
}

function WithItemFilters(props: WithItemFiltersProps) {
  const router = useRouter();
  const stringifiedQuery = qs.stringify(router.query);
  const paramsFromQuery = parseFilterParamsFromQs(stringifiedQuery);
  const initialFilters = {
    ...(props.initialFilters || DEFAULT_FILTER_PARAMS),
    ...paramsFromQuery,
  };

  const filterState = withFilters(initialFilters, props.initialFilters);

  const memoedFilterState = useCustomCompareMemo(
    () => {
      return { ...filterState, defaultFilters: props.initialFilters };
    },
    [filterState],
    hookDeepEqual,
  );

  useEffectCompare(
    () => {
      filterState.setFilters(initialFilters);
    },
    [initialFilters],
    (prevDeps, nextDeps) => hookDeepEqual(prevDeps, nextDeps),
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
