import { DependencyList, EffectCallback, useCallback } from 'react';
import { checkDeps, useCustomCompareMemoize } from './useMemoCompare';

export default function useCustomCompareCallback<
  T extends (...args: any[]) => any
>(
  callback: T,
  deps: DependencyList,
  depsAreEqual: (prevDeps: DependencyList, nextDeps: DependencyList) => boolean,
) {
  if (process.env.NODE_ENV !== 'production') {
    checkDeps(deps, depsAreEqual, 'useCustomCompareCallback');
  }

  return useCallback(callback, useCustomCompareMemoize(deps, depsAreEqual));
}
