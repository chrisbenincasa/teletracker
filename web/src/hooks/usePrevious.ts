import { DependencyList, useEffect, useRef } from 'react';
import useEffectCompare from './useEffectCompare';
import dequal from 'dequal';

// https://usehooks.com/usePrevious/
export function usePrevious<T>(value: T): T | undefined {
  // The ref object is a generic container whose current property is mutable ...
  // ... and can hold any value, similar to an instance property on a class
  const ref = useRef<T>();

  // Store current value in ref
  useEffect(() => {
    ref.current = value;
  }, [value]); // Only re-run if value changes

  // Return previous value (happens before update in useEffect above)
  return ref.current;
}

export function usePreviousDeepEq<T>(
  value: T,
  equalityFn?: (left: T, right: T) => boolean,
) {
  // The ref object is a generic container whose current property is mutable ...
  // ... and can hold any value, similar to an instance property on a class
  const ref = useRef<T>();

  const compareFn = (left: DependencyList, right: DependencyList) => {
    if (equalityFn) {
      return equalityFn(left[0], right[0]);
    } else {
      return dequal(left, right);
    }
  };

  // Store current value in ref
  useEffectCompare(
    () => {
      ref.current = value;
    },
    [value],
    compareFn,
  ); // Only re-run if value changes

  // Return previous value (happens before update in useEffect above)
  return ref.current;
}
