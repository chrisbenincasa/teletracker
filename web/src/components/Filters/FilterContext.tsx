import React, { createContext, ReactNode, useCallback } from 'react';
import { FilterParams } from '../../utils/searchFilters';
import { useStateDeepEq } from '../../hooks/useStateDeepEq';
import { filterParamsEqual } from '../../utils/changeDetection';
import useCustomCompareMemo from '../../hooks/useMemoCompare';
import dequal from 'dequal';

export interface FilterContextState {
  filters: FilterParams;
  setFilters: (newFilters: FilterParams) => void;
}

export const FilterContext = createContext<FilterContextState>({
  filters: {},
  setFilters: () => {},
});

interface WithItemFiltersProps {
  defaultFilters?: FilterParams;
  children: ReactNode;
}

export function WithItemFilters(props: WithItemFiltersProps) {
  const filterState = withFilters(props.defaultFilters);
  const memoedFilterState = useCustomCompareMemo(
    () => ({ ...filterState }),
    [filterState],
    dequal,
  );

  return (
    <FilterContext.Provider value={memoedFilterState}>
      {props.children}
    </FilterContext.Provider>
  );
}

function withFilters(defaultFilters?: FilterParams): FilterContextState {
  const [filters, setFilters] = useStateDeepEq(
    defaultFilters || {},
    filterParamsEqual,
  );

  const actuallySetFilters = useCallback(
    (newFilters: FilterParams) => {
      setFilters(newFilters);
    },
    [filters],
  );

  return {
    filters,
    setFilters: actuallySetFilters,
  };
}
