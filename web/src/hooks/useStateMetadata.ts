import useStateSelector from './useStateSelector';
import dequal from 'dequal';

export function useMetadata() {
  return useStateSelector(state => state.metadata, dequal);
}

export function useGenres() {
  return useStateSelector(state => state.metadata.genres, dequal);
}

export function useNetworks() {
  return useStateSelector(state => state.metadata.networks, dequal);
}
