import { useDispatch } from 'react-redux';

/**
 * Convenience hook for dispatching actions to Redux using only a payload.
 *
 * Originally:
 *   const dispatch = useDispatch();
 *
 *   // BAD - you have to remember the type, or import some other action creator
 *   dispatch({type: 'XYZ', payload: someObject})
 *
 * Example:
 *   const fetchUser = useDispatchAction(fetchUserInitiated);
 *
 *   // GOOD - import the action creator and only reference it once in the hook.
 *   // Then, only use the payload when calling the function.
 *   fetchUser({id: userId})
 *
 * @param fn
 */
export function useDispatchAction<T, U>(fn: (payload: T | undefined) => U) {
  const dispatch = useDispatch();

  return (payload: T | undefined) => {
    dispatch(fn(payload));
  };
}
