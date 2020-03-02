import { put, takeLatest } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { createAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { TeletrackerResponse } from '../../utils/api-client';
import _ from 'lodash';
import { Paging } from '../../types';
import { ApiPerson } from '../../types/v2';
import { Person, PersonFactory } from '../../types/v2/Person';

export const PEOPLE_SEARCH_INITIATED = 'search/people/INITIATED';
export const PEOPLE_SEARCH_SUCCESSFUL = 'search/people/SUCCESSFUL';
export const PEOPLE_SEARCH_FAILED = 'search/people/FAILED';

export interface PeopleSearchInitiatedPayload {
  query: string;
  limit?: number;
  bookmark?: string;
}

export type PeopleSearchInitiatedAction = FSA<
  typeof PEOPLE_SEARCH_INITIATED,
  PeopleSearchInitiatedPayload
>;
export interface PeopleSearchSuccessfulPayload {
  results: Person[];
  paging?: Paging;
  append: boolean;
}

export type PeopleSearchSuccessfulAction = FSA<
  typeof PEOPLE_SEARCH_SUCCESSFUL,
  PeopleSearchSuccessfulPayload
>;

// TODO: Could fold this into a single action type "SearchCompleted"
export type PeopleSearchFailedAction = FSA<typeof PEOPLE_SEARCH_FAILED, Error>;

const PeopleSearchInitiated = createAction<PeopleSearchInitiatedAction>(
  PEOPLE_SEARCH_INITIATED,
);
const PeopleSearchSuccess = createAction<PeopleSearchSuccessfulAction>(
  PEOPLE_SEARCH_SUCCESSFUL,
);
const PeopleSearchFailed = createAction<PeopleSearchFailedAction>(
  PEOPLE_SEARCH_FAILED,
);

export type PeopleSearchActionTypes =
  | PeopleSearchFailedAction
  | PeopleSearchInitiatedAction
  | PeopleSearchSuccessfulAction;

export const peopleSearchSaga = function*() {
  yield takeLatest(PEOPLE_SEARCH_INITIATED, function*({
    payload,
  }: PeopleSearchInitiatedAction) {
    try {
      let response: TeletrackerResponse<ApiPerson[]> = yield clientEffect(
        client => client.searchPeople,
        payload!.query,
        payload!.limit,
        payload!.bookmark,
      );

      if (response.ok) {
        let successPayload = {
          results: response.data!.data.map(PersonFactory.create),
          paging: response.data!.paging,
          append: !_.isUndefined(payload!.bookmark),
        };

        yield put(PeopleSearchSuccess(successPayload));
      } else {
        yield put(PeopleSearchFailed(new Error(response.problem)));
      }
    } catch (e) {
      yield put(PeopleSearchFailed(e));
    }
  });
};

export const searchPeople = (payload: PeopleSearchInitiatedPayload) => {
  return PeopleSearchInitiated(payload);
};
