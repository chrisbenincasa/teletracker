import { useState } from 'react';

/**
 * Convenience hook for toggling a boolean state
 * @param initial
 */
export default function useToggle(initial: boolean): [boolean, () => void] {
  const [state, setState] = useState(initial);

  const dispatch = () => {
    setState(old => !old);
  };

  return [state, dispatch];
}
