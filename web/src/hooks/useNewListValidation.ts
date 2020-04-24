import { useStateDeepEq } from './useStateDeepEq';
import useStateSelector from './useStateSelector';
import { hookDeepEqual } from './util';
import { useCallback } from 'react';
import CreateAListValidator from '../utils/validation/CreateAListValidator';

export default function useNewListValidation() {
  const listsById = useStateSelector(
    state => state.lists.listsById,
    hookDeepEqual,
  );

  return useCallback(
    (newListName: string) => {
      return CreateAListValidator.validate(listsById, newListName);
    },
    [listsById],
  );
}
