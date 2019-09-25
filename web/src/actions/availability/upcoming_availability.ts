import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { defaultMovieMeta } from '../lists';
import { ErrorFSA, FSA } from 'flux-standard-action';
import Thing, { ThingFactory } from '../../types/Thing';

export const UPCOMING_AVAILABILITY_INITIATED =
  'availability/upcoming/INITIATED';
export const UPCOMING_AVAILABILITY_SUCCESSFUL =
  'availability/upcoming/SUCCESSFUL';
export const UPCOMING_AVAILABILITY_FAILED = 'availability/upcoming/FAILED';

export type UpcomingAvailabilityInitiatedAction = FSA<
  typeof UPCOMING_AVAILABILITY_INITIATED
>;

export interface UpcomingAvailabilitySuccessfulPayload {
  upcoming: Thing[];
  expiring: Thing[];
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
      let response = yield clientEffect(
        client => client.getUpcomingAvailability,
        undefined,
        defaultMovieMeta,
      );

      if (response.ok) {
        yield put(
          upcomingAvailabilitySuccess({
            upcoming: response.data.data.future.upcoming.map(
              ThingFactory.create,
            ),
            expiring: response.data.data.future.upcoming.map(
              ThingFactory.create,
            ),
          }),
        );
      }
    } catch (e) {
      yield put(upcomingAvailabilityFailed(e));
    }
  });
};
