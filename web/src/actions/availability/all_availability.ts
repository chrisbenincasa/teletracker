import { put, takeEvery } from '@redux-saga/core/effects';
import { createAction, createBasicAction } from '../utils';
import { clientEffect } from '../clientEffect';
import { defaultMovieMeta } from '../lists';
import {
  UPCOMING_AVAILABILITY_INITIATED,
  UpcomingAvailabilitySuccessfulPayload,
} from './upcoming_availability';
import { ErrorFSA, FSA } from 'flux-standard-action';
import { Item, ItemFactory } from '../../types/v2/Item';

export const ALL_AVAILABILITY_INITIATED = 'availability/all/INITIATED';
export const ALL_AVAILABILITY_SUCCESSFUL = 'availability/all/SUCCESSFUL';
export const ALL_AVAILABILITY_FAILED = 'availability/all/FAILED';

export type AllAvailabilityInitiatedAction = FSA<
  typeof ALL_AVAILABILITY_INITIATED
>;

export interface AllAvailabilitySuccessfulPayload {
  recentlyAdded: Item[];
  future: UpcomingAvailabilitySuccessfulPayload;
}

export type AllAvailabilitySuccessfulAction = FSA<
  typeof ALL_AVAILABILITY_SUCCESSFUL,
  AllAvailabilitySuccessfulPayload
>;
export type AllAvailabilityFailedAction = ErrorFSA<
  Error,
  undefined,
  typeof ALL_AVAILABILITY_FAILED
>;

export const retrieveAllAvailability = createBasicAction<
  AllAvailabilityInitiatedAction
>(ALL_AVAILABILITY_INITIATED);

export const allAvailabilitySuccess = createAction<
  AllAvailabilitySuccessfulAction
>(ALL_AVAILABILITY_SUCCESSFUL);

export const allAvailabilityFailed = createAction<AllAvailabilityFailedAction>(
  ALL_AVAILABILITY_FAILED,
);

export const allAvailabilitySaga = function*() {
  yield takeEvery(UPCOMING_AVAILABILITY_INITIATED, function*() {
    try {
      let response = yield clientEffect(
        client => client.getAllAvailability,
        undefined,
        defaultMovieMeta,
      );

      if (response.ok) {
        yield put(
          allAvailabilitySuccess({
            recentlyAdded: response.data.data.recentlyAdded.map(
              ItemFactory.create,
            ),
            future: {
              upcoming: response.data.data.future.upcoming.map(
                ItemFactory.create,
              ),
              expiring: response.data.data.future.upcoming.map(
                ItemFactory.create,
              ),
            },
          }),
        );
      }
    } catch (e) {
      yield put(allAvailabilityFailed(e));
    }
  });
};
