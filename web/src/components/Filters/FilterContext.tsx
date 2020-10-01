import React, { createContext, ReactNode, useEffect, useMemo } from 'react';
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
import useEffectCompare from '../../hooks/useEffectCompare';
import { useRouter } from 'next/router';
import useCustomCompareCallback from '../../hooks/useCallbackCompare';

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
  defaultFilters: DEFAULT_FILTER_PARAMS,
});

interface WithItemFiltersProps {
  readonly initialFilters?: FilterParams;
  readonly children: ReactNode;
}

function WithItemFilters(props: WithItemFiltersProps) {
  const router = useRouter();
  const stringifiedQuery = qs.stringify(router.query);
  const paramsFromQuery = parseFilterParamsFromQs(stringifiedQuery);
  const normalizedInitialFilters =
    props.initialFilters || DEFAULT_FILTER_PARAMS;
  const initialFilters = {
    ...normalizedInitialFilters,
    ...paramsFromQuery,
  };

  const [filters, setFilters] = useStateDeepEq(
    {
      filters: initialFilters,
      defaultFilters: props.initialFilters,
      currentFiltersAreDefault: filterParamsEqual(
        initialFilters,
        props.initialFilters,
        props.initialFilters?.sortOrder,
      ),
    },
    (left, right) => {
      return (
        filterParamsEqual(left.filters, right.filters) &&
        filterParamsEqual(left.defaultFilters, right.defaultFilters)
      );
    },
  );

  useEffectCompare(
    () => {
      let newFilters = {
        ...normalizedInitialFilters,
        ...paramsFromQuery,
      };

      setFilters({
        filters: newFilters,
        defaultFilters: props.initialFilters,
        currentFiltersAreDefault: filterParamsEqual(
          newFilters,
          props.initialFilters,
        ),
      });
    },
    [normalizedInitialFilters],
    ([left], [right]) => filterParamsEqual(left, right),
  );

  const actuallySetFilters = useCustomCompareCallback(
    (newFilters: FilterParams) => {
      const normalized = normalizeFilterParams(newFilters);
      setFilters(prev => {
        return {
          ...prev,
          filters: normalized,
          currentFiltersAreDefault: filterParamsEqual(
            normalized,
            prev.defaultFilters,
            prev.defaultFilters?.sortOrder,
          ),
        };
      });
    },
    [filters],
    ([left], [right]) => filterParamsEqual(left, right),
  );

  const clearFilters = useCustomCompareCallback(
    () => {
      actuallySetFilters(normalizedInitialFilters);
    },
    [normalizedInitialFilters],
    ([left], [right]) => filterParamsEqual(left, right),
  );

  // Update query params if either the applied or default filters change
  useEffect(() => {
    updateUrlParamsForNextRouter(
      router,
      filters.filters,
      [],
      filters.defaultFilters,
    );
  }, [filters.filters, filters.defaultFilters]);

  // Memoize the whole context state
  const wholeState = useCustomCompareMemo<FilterContextState>(
    () => {
      return {
        filters: filters.filters,
        defaultFilters: filters.defaultFilters,
        currentFiltersAreDefault: filters.currentFiltersAreDefault,
        clearFilters: clearFilters,
        setFilters: actuallySetFilters,
      };
    },
    [filters, actuallySetFilters, clearFilters],
    (prev, next) => {
      const [prevFilters, prevSet, prevClear] = prev;
      const [nextFilters, nextSet, nextClear] = next;
      return (
        filterParamsEqual(prevFilters.filters, nextFilters.filters) &&
        filterParamsEqual(
          prevFilters.defaultFilters,
          nextFilters.defaultFilters,
        ) &&
        prevSet === nextSet &&
        prevClear === nextClear
      );
    },
  );

  return (
    <FilterContext.Provider value={wholeState}>
      {props.children}
    </FilterContext.Provider>
  );
}

// WithItemFilters.whyDidYouRender = true;

export default React.memo(WithItemFilters, dequal);
