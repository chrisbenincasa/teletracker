import { AppState } from '../reducers';
import { useSelector } from 'react-redux';
import { usePrevious } from './usePrevious';
import { useDebugValue } from 'react';
import { hookDeepEqual } from './util';

export type AppStateSelector<T> = (state: AppState) => T;

/**
 * Convenience wrapper around {@link useSelector} that adds our state type
 * automatically
 * @param selector
 * @param equalityFn
 */
export default function useStateSelector<Selected>(
  selector: AppStateSelector<Selected>,
  equalityFn: (left: Selected, right: Selected) => boolean = hookDeepEqual,
  name?: string,
): Selected {
  let value = useSelector((state: AppState) => selector(state), equalityFn);
  if (name) {
    useDebugValue(`${name}: ${value}`);
  }
  return value;
}

/**
 * Wrapper around {@link useSelector} and {@link usePrevious} to keep track of
 * the previously seen Redux state for a selector
 * @param selector
 * @param equalityFn
 */
export function useStateSelectorWithPrevious<Selected>(
  selector: AppStateSelector<Selected>,
  equalityFn: (left: Selected, right: Selected) => boolean = hookDeepEqual,
): [Selected, Selected | undefined] {
  const value = useStateSelector(selector, equalityFn);
  const previous = usePrevious(value);
  return [value, previous];
}
