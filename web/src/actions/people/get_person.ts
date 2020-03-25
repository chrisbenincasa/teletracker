import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { put, takeEvery } from '@redux-saga/core/effects';
import { Person, PersonFactory } from '../../types/v2/Person';
import { TeletrackerResponse } from '../../utils/api-client';
import { ApiPerson } from '../../types/v2/Person';

export const PERSON_FETCH_INITIATED = 'person/fetch/INITIATED';
export const PERSON_FETCH_SUCCESSFUL = 'person/fetch/SUCCESSFUL';
export const PERSON_FETCH_FAILED = 'person/fetch/FAILED';
export const PERSON_DETAIL_PAGE_SET_CURRENT = 'person/detail/SET_CURRENT';

export interface PersonFetchInitiatedPayload {
  id: string;
  forDetailPage: boolean;
}

export type PersonFetchInitiatedAction = FSA<
  typeof PERSON_FETCH_INITIATED,
  PersonFetchInitiatedPayload
>;

export interface PersonFetchSuccessfulPayload {
  person: Person;
  rawPerson: ApiPerson;
}

export type PersonFetchSuccessfulAction = FSA<
  typeof PERSON_FETCH_SUCCESSFUL,
  PersonFetchSuccessfulPayload
>;

export type PersonFetchFailedAction = ErrorFluxStandardAction<
  typeof PERSON_FETCH_FAILED
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
      let response: TeletrackerResponse<ApiPerson> = yield clientEffect(
        client => client.getPerson,
        payload.id,
      );

      if (response.ok && response.data) {
        yield put(
          personFetchSuccess({
            person: PersonFactory.create(response.data.data),
            rawPerson: response.data.data,
          }),
        );
      } else {
        yield put(personFetchFailed(new Error()));
      }
    }
  });
};
