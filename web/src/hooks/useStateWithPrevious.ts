import { Dispatch, useState } from 'react';
import { usePrevious } from './usePrevious';

/**
 * Convenience wrapper for combining {@link useState} and {@link usePrevious}
 * @param initial
 */
export function useStateWithPrevious<T>(
  initial: T,
): [T, Dispatch<T>, T | undefined] {
  const [value, setValue] = useState(initial);
  const previousValue = usePrevious(value);
  return [value, setValue, previousValue];
}
