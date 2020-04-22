import { createSelector } from 'reselect';
import { AppState } from '../../reducers';
import { Person } from '../../types/v2/Person';
import { extractValue } from '../../utils/collection-utils';

export const selectPerson = createSelector(
  (state: AppState) => state.people.peopleById,
  (state: AppState) => state.people.detail?.current,
  (_, personId) => personId,
  (peopleById, currentPerson, personId) => {
    const person: Person | undefined =
      peopleById[personId] || extractValue(personId, undefined, peopleById);

    return person;
  },
);
