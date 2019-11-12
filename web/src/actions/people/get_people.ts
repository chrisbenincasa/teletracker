import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { clientEffect, createAction } from '../utils';
import { put, takeEvery } from '@redux-saga/core/effects';
import { Person, PersonFactory } from '../../types/v2/Person';
import { Id, Slug } from '../../types/v2';

export const PEOPLE_FETCH_INITIATED = 'people/fetch/INITIATED';
export const PEOPLE_FETCH_SUCCESSFUL = 'people/fetch/SUCCESSFUL';
export const PEOPLE_FETCH_FAILED = 'people/fetch/FAILED';

export interface PeopleFetchInitiatedPayload {
  ids: (Id | Slug)[];
}

export type PeopleFetchInitiatedAction = FSA<
  typeof PEOPLE_FETCH_INITIATED,
  PeopleFetchInitiatedPayload
>;

export type PeopleFetchSuccessfulAction = FSA<
  typeof PEOPLE_FETCH_SUCCESSFUL,
  Person[]
>;

export type PeopleFetchFailedAction = ErrorFluxStandardAction<
  typeof PEOPLE_FETCH_FAILED,
  Error
>;

export const peopleFetchInitiated = createAction<PeopleFetchInitiatedAction>(
  PEOPLE_FETCH_INITIATED,
);

export const peopleFetchSuccess = createAction<PeopleFetchSuccessfulAction>(
  PEOPLE_FETCH_SUCCESSFUL,
);

const peopleFetchFailed = createAction<PeopleFetchFailedAction>(
  PEOPLE_FETCH_FAILED,
);

export const fetchPeopleDetailsSaga = function*() {
  yield takeEvery(PEOPLE_FETCH_INITIATED, function*({
    payload,
  }: PeopleFetchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(
        client => client.getPeople,
        payload.ids,
      );

      if (response.ok) {
        yield put(
          peopleFetchSuccess(response.data.data.map(PersonFactory.create)),
        );
      } else {
        yield put(peopleFetchFailed(new Error()));
      }
    }
  });
};
