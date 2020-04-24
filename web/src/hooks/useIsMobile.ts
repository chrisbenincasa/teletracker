import { useWidth } from './useWidth';

export default function useIsMobile() {
  return ['xs', 'sm'].includes(useWidth());
}
