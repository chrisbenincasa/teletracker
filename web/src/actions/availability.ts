import { put, takeEvery } from '@redux-saga/core/effects';
import { ErrorFSA, FSA } from 'flux-standard-action';
import {
  UPCOMING_AVAILABILITY_FAILED,
  UPCOMING_AVAILABILITY_INITIATED,
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  ALL_AVAILABILITY_INITIATED,
  ALL_AVAILABILITY_SUCCESSFUL,
  ALL_AVAILABILITY_FAILED,
} from '../constants/availability';
import { Availability } from '../types';
import { defaultMovieMeta } from './lists';
import { clientEffect, createAction, createBasicAction } from './utils';

export type UpcomingAvailabilityInitiatedAction = FSA<
  typeof UPCOMING_AVAILABILITY_INITIATED
>;

export interface UpcomingAvailabilitySuccessfulPayload {
  upcoming: Availability[];
  expiring: Availability[];
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

export type AllAvailabilityInitiatedAction = FSA<
  typeof ALL_AVAILABILITY_INITIATED
>;

export interface AllAvailabilitySuccessfulPayload {
  recentlyAdded: Availability[];
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

export const retrieveUpcomingAvailability = createBasicAction<
  UpcomingAvailabilityInitiatedAction
>(UPCOMING_AVAILABILITY_INITIATED);

export const upcomingAvailabilitySuccess = createAction<
  UpcomingAvailabilitySuccessfulAction
>(UPCOMING_AVAILABILITY_SUCCESSFUL);

export const upcomingAvailabilityFailed = createAction<
  UpcomingAvailabilityFailedAction
>(UPCOMING_AVAILABILITY_FAILED);

export const retrieveAllAvailability = createBasicAction<
  AllAvailabilityInitiatedAction
>(ALL_AVAILABILITY_INITIATED);

export const allAvailabilitySuccess = createAction<
  AllAvailabilitySuccessfulAction
>(ALL_AVAILABILITY_SUCCESSFUL);

export const allAvailabilityFailed = createAction<AllAvailabilityFailedAction>(
  ALL_AVAILABILITY_FAILED,
);

export type AvailabilityActionTypes =
  | UpcomingAvailabilityInitiatedAction
  | UpcomingAvailabilitySuccessfulAction;

export const upcomingAvailabilitySaga = function*() {
  yield takeEvery(UPCOMING_AVAILABILITY_INITIATED, function*() {
    try {
      let response = yield clientEffect(
        client => client.getUpcomingAvailability,
        undefined,
        defaultMovieMeta,
      );

      if (response.ok) {
        yield put(upcomingAvailabilitySuccess(response.data.data));
      }
    } catch (e) {
      yield put(upcomingAvailabilityFailed(e));
    }
  });
};

export const allAvailabilitySaga = function*() {
  yield takeEvery(UPCOMING_AVAILABILITY_INITIATED, function*() {
    try {
      let response = yield clientEffect(
        client => client.getAllAvailability,
        undefined,
        defaultMovieMeta,
      );

      if (response.ok) {
        yield put(allAvailabilitySuccess(response.data.data));
      }
    } catch (e) {
      yield put(allAvailabilityFailed(e));
    }
  });
};
