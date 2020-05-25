import { DependencyList, useMemo, useRef } from 'react';
import _ from 'lodash';

function isPrimitive(val: any) {
  return val == null || /^[sbn]/.test(typeof val);
}

export function checkDeps(
  deps: DependencyList,
  depsAreEqual: (prevDeps: DependencyList, nextDeps: DependencyList) => boolean,
  name: string,
) {
  const reactHookName = `React.${name.replace(/CustomCompare/, '')}`;

  if (!_.isArray(deps) || deps.length === 0) {
    throw new Error(
      `${name} should not be used with no dependencies. Use ${reactHookName} instead.`,
    );
  }
  if (deps.every(isPrimitive)) {
    throw new Error(
      `${name} should not be used with dependencies that are all primitive values. Use ${reactHookName} instead.`,
    );
  }
  if (typeof depsAreEqual !== 'function') {
    throw new Error(
      `${name} should be used with depsEqual callback for comparing deps list`,
    );
  }
}

export function useCustomCompareMemoize(
  deps: DependencyList,
  depsAreEqual: (prevDeps: DependencyList, nextDeps: DependencyList) => boolean,
) {
  const ref = useRef<DependencyList | undefined>(undefined);

  if (!ref.current || !depsAreEqual(deps, ref.current)) {
    ref.current = deps;
  }

  return ref.current;
}

export default function useCustomCompareMemo<T>(
  factory: () => T,
  deps: DependencyList,
  depsAreEqual: (prevDeps: DependencyList, nextDeps: DependencyList) => boolean,
): T {
  if (process.env.NODE_ENV !== 'production') {
    checkDeps(deps, depsAreEqual, 'useCustomCompareMemo');
  }

  return useMemo(factory, useCustomCompareMemoize(deps, depsAreEqual));
}
