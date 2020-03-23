import useStateSelector from './useStateSelector';
import { extractValue } from '../utils/collection-utils';
import { Person } from '../types/v2/Person';

// TODO: We need this to take an itemType to be totally correct
export default function usePerson(
  id: string,
  initialPerson?: Person,
): Person | undefined {
  const peopleById = useStateSelector(state => state.people.peopleById);

  return extractValue(id, initialPerson, peopleById);
}
