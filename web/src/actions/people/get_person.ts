import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { clientEffect, createAction } from '../utils';
import { put, takeEvery } from '@redux-saga/core/effects';
import Person, { PersonFactory } from '../../types/Person';

export const PERSON_FETCH_INITIATED = 'person/fetch/INITIATED';
export const PERSON_FETCH_SUCCESSFUL = 'person/fetch/SUCCESSFUL';
export const PERSON_FETCH_FAILED = 'person/fetch/FAILED';

export interface PersonFetchInitiatedPayload {
  id: string;
}

export type PersonFetchInitiatedAction = FSA<
  typeof PERSON_FETCH_INITIATED,
  PersonFetchInitiatedPayload
>;

export type PersonFetchSuccessfulAction = FSA<
  typeof PERSON_FETCH_SUCCESSFUL,
  Person
>;

export type PersonFetchFailedAction = ErrorFluxStandardAction<
  typeof PERSON_FETCH_FAILED,
  Error
>;

export const personFetchInitiated = createAction<PersonFetchInitiatedAction>(
  PERSON_FETCH_INITIATED,
);

export const personFetchSuccess = createAction<PersonFetchSuccessfulAction>(
  PERSON_FETCH_SUCCESSFUL,
);

const personFetchFailed = createAction<PersonFetchFailedAction>(
  PERSON_FETCH_FAILED,
);

export const fetchPersonDetailsSaga = function*() {
  yield takeEvery(PERSON_FETCH_INITIATED, function*({
    payload,
  }: PersonFetchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(client => client.getPerson, payload.id);

      if (response.ok) {
        yield put(personFetchSuccess(PersonFactory.create(response.data.data)));
      } else {
        yield put(personFetchFailed(new Error()));
      }
    }
  });
};
