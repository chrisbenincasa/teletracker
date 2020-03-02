import { put, takeEvery } from '@redux-saga/core/effects';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { defaultMovieMeta } from '../lists';
import { ErrorFSA, FSA } from 'flux-standard-action';
import { Item, ItemFactory } from '../../types/v2/Item';
import { TeletrackerResponse } from '../../utils/api-client';
import { ApiItem } from '../../types/v2';

export const UPCOMING_AVAILABILITY_INITIATED =
  'availability/upcoming/INITIATED';
export const UPCOMING_AVAILABILITY_SUCCESSFUL =
  'availability/upcoming/SUCCESSFUL';
export const UPCOMING_AVAILABILITY_FAILED = 'availability/upcoming/FAILED';

export type UpcomingAvailabilityInitiatedAction = FSA<
  typeof UPCOMING_AVAILABILITY_INITIATED
>;

export interface UpcomingAvailabilitySuccessfulPayload {
  upcoming: Item[];
  expiring: Item[];
}

export type UpcomingAvailabilitySuccessfulAction = FSA<
  typeof UPCOMING_AVAILABILITY_SUCCESSFUL,
  UpcomingAvailabilitySuccessfulPayload
>;
export type UpcomingAvailabilityFailedAction = ErrorFSA<
  Error,
  undefined,
  typeof UPCOMING_AVAILABILITY_FAILED
>;

export const retrieveUpcomingAvailability = createBasicAction<
  UpcomingAvailabilityInitiatedAction
>(UPCOMING_AVAILABILITY_INITIATED);

export const upcomingAvailabilitySuccess = createAction<
  UpcomingAvailabilitySuccessfulAction
>(UPCOMING_AVAILABILITY_SUCCESSFUL);

export const upcomingAvailabilityFailed = createAction<
  UpcomingAvailabilityFailedAction
>(UPCOMING_AVAILABILITY_FAILED);

export const upcomingAvailabilitySaga = function*() {
  yield takeEvery(UPCOMING_AVAILABILITY_INITIATED, function*() {
    try {
      let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
        client => client.getUpcomingAvailability,
        undefined,
        defaultMovieMeta,
      );

      if (response.ok) {
        yield put(
          upcomingAvailabilitySuccess({
            upcoming: response.data!.data.map(ItemFactory.create),
            expiring: [],
          }),
        );
      }
    } catch (e) {
      yield put(upcomingAvailabilityFailed(e));
    }
  });
};
