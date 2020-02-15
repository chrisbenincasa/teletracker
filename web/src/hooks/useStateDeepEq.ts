import { Dispatch, useState } from 'react';
import { useStateWithPrevious } from './useStateWithPrevious';

/**
 * Version of {@link useState} that uses a custom equality check before
 * changing the underlying state value.
 * @param value
 * @param equalityCheck
 */
export function useStateDeepEq<T>(
  value: T,
  equalityCheck: (left: T, right: T) => boolean,
): [T, Dispatch<T>] {
  const [v, actuallySetValue] = useState(value);

  const dispatch = (newValue: T) => {
    if (!equalityCheck(v, newValue)) {
      actuallySetValue(newValue);
    }
  };

  return [v, dispatch];
}

/**
 * Convenience wrapper combining {@link useStateDeepEq}
 * and {@link useStateWithPrevious}
 * @param value
 * @param equalityCheck
 */
export function useStateDeepEqWithPrevious<T>(
  value: T,
  equalityCheck: (left: T, right: T) => boolean,
): [T, Dispatch<T>, T | undefined] {
  const [v, actuallySetValue, previous] = useStateWithPrevious(value);

  const dispatch = (newValue: T) => {
    if (!equalityCheck(v, newValue)) {
      actuallySetValue(newValue);
    }
  };

  return [v, dispatch, previous];
}
