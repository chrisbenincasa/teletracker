import { AppState } from '../reducers';
import { useSelector } from 'react-redux';
import { usePrevious } from './usePrevious';

/**
 * Convenience wrapper around {@link useSelector} that adds our state type
 * automatically
 * @param selector
 * @param equalityFn
 */
export default function useStateSelector<Selected>(
  selector: (state: AppState) => Selected,
  equalityFn?: (left: Selected, right: Selected) => boolean,
): Selected {
  return useSelector((state: AppState) => selector(state), equalityFn);
}

/**
 * Wrapper around {@link useSelector} and {@link usePrevious} to keep track of
 * the previously seen Redux state for a selector
 * @param selector
 * @param equalityFn
 */
export function useStateSelectorWithPrevious<Selected>(
  selector: (state: AppState) => Selected,
  equalityFn?: (left: Selected, right: Selected) => boolean,
): [Selected, Selected | undefined] {
  const value = useSelector((state: AppState) => selector(state), equalityFn);
  const previous = usePrevious(value);

  return [value, previous];
}
