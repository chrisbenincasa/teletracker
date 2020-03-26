import { ErrorFluxStandardAction, FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { put, takeEvery } from '@redux-saga/core/effects';
import { Item, ItemFactory } from '../../types/v2/Item';
import { Paging } from '../../types';
import _ from 'lodash';
import { Id, Slug } from '../../types/v2';
import { FilterParams } from '../../utils/searchFilters';

export const PERSON_CREDITS_FETCH_INITIATED = 'person/fetch/credits/INITIATED';
export const PERSON_CREDITS_FETCH_SUCCESSFUL =
  'person/fetch/credits/SUCCESSFUL';
export const PERSON_CREDITS_FETCH_FAILED = 'person/fetch/credits/FAILED';

export interface PersonCreditsFetchInitiatedPayload {
  personId: Id | Slug;
  filterParams?: FilterParams;
  limit?: number;
  bookmark?: string;
}

export type PersonCreditsFetchInitiatedAction = FSA<
  typeof PERSON_CREDITS_FETCH_INITIATED,
  PersonCreditsFetchInitiatedPayload
>;

export interface PersonCreditsFetchSuccessfulPayload {
  personId: Id | Slug;
  credits: Item[];
  paging?: Paging;
  append: boolean;
}

export type PersonCreditsFetchSuccessfulAction = FSA<
  typeof PERSON_CREDITS_FETCH_SUCCESSFUL,
  PersonCreditsFetchSuccessfulPayload
>;

export type PersonCreditsFetchFailedAction = ErrorFluxStandardAction<
  typeof PERSON_CREDITS_FETCH_FAILED,
  Error
>;

export const personCreditsFetchInitiated = createAction<
  PersonCreditsFetchInitiatedAction
>(PERSON_CREDITS_FETCH_INITIATED);

export const personCreditsFetchSuccess = createAction<
  PersonCreditsFetchSuccessfulAction
>(PERSON_CREDITS_FETCH_SUCCESSFUL);

const personCreditsFetchFailed = createAction<PersonCreditsFetchFailedAction>(
  PERSON_CREDITS_FETCH_FAILED,
);

export const fetchPersonCreditsDetailsSaga = function*() {
  yield takeEvery(PERSON_CREDITS_FETCH_INITIATED, function*({
    payload,
  }: PersonCreditsFetchInitiatedAction) {
    if (payload) {
      let response = yield clientEffect(
        client => client.getPersonCredits,
        payload.personId,
        payload.filterParams,
        payload.limit,
        payload.bookmark,
        ['cast'],
      );

      if (response.ok) {
        yield put(
          personCreditsFetchSuccess({
            personId: payload.personId,
            credits: response.data.data.map(ItemFactory.create),
            paging: response.data!.paging,
            append: !_.isUndefined(payload.bookmark),
          }),
        );
      } else {
        yield put(personCreditsFetchFailed(new Error()));
      }
    }
  });
};
