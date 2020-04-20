import { useCallback } from 'react';

export default function useToggleCallback(
  toggle: (fn: (x: boolean) => boolean) => void,
) {
  return useCallback(() => {
    toggle(prev => !prev);
  }, []);
}
