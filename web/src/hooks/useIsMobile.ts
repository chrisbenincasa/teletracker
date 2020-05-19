import { useWidth } from './useWidth';

export default function useIsMobile() {
  return ['xs', 'sm'].includes(useWidth());
}

export function useIsSmallScreen() {
  return ['xs', 'sm', 'md'].includes(useWidth());
}
