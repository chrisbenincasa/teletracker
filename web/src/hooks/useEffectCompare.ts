import { DependencyList, EffectCallback, useEffect } from 'react';
import { checkDeps, useCustomCompareMemoize } from './useMemoCompare';

export default function useCustomCompareEffect(
  effect: EffectCallback,
  deps: DependencyList,
  depsAreEqual: (prevDeps: DependencyList, nextDeps: DependencyList) => boolean,
) {
  if (process.env.NODE_ENV !== 'production') {
    checkDeps(deps, depsAreEqual, 'useCustomCompareEffect');
  }

  useEffect(effect, useCustomCompareMemoize(deps, depsAreEqual));
}
