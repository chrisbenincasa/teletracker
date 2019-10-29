import { put, takeEvery } from '@redux-saga/core/effects';
import { clientEffect, createAction, createBasicAction } from '../utils';
import { defaultMovieMeta } from '../lists';
import {
  UPCOMING_AVAILABILITY_INITIATED,
  UpcomingAvailabilitySuccessfulPayload,
} from './upcoming_availability';
import { ErrorFSA, FSA } from 'flux-standard-action';
import { Availability } from '../../types';
import Thing, { ThingFactory } from '../../types/Thing';
import { Item, ItemFactory } from '../../types/v2/Item';

export const NETWORK_AVAILABILITY_INITIATED = 'availability/network/INITIATED';
export const NETWORK_AVAILABILITY_SUCCESSFUL =
  'availability/network/SUCCESSFUL';
export const NETWORK_AVAILABILITY_FAILED = 'availability/network/FAILED';

export interface NetworkAvailabilityInitiatedPayload {
  networks: number[];
}

export type NetworkAvailabilityInitiatedAction = FSA<
  typeof NETWORK_AVAILABILITY_INITIATED,
  NetworkAvailabilityInitiatedPayload
>;

export interface NetworkAvailabilitySuccessfulPayload {
  items: Item[];
}

export type NetworkAvailabilitySuccessfulAction = FSA<
  typeof NETWORK_AVAILABILITY_SUCCESSFUL,
  NetworkAvailabilitySuccessfulPayload
>;
export type NetworkAvailabilityFailedAction = ErrorFSA<
  Error,
  undefined,
  typeof NETWORK_AVAILABILITY_FAILED
>;

export const retrieveNetworkAvailability = createAction<
  NetworkAvailabilityInitiatedAction
>(NETWORK_AVAILABILITY_INITIATED);

export const networkAvailabilitySuccess = createAction<
  NetworkAvailabilitySuccessfulAction
>(NETWORK_AVAILABILITY_SUCCESSFUL);

export const networkAvailabilityFailed = createAction<
  NetworkAvailabilityFailedAction
>(NETWORK_AVAILABILITY_FAILED);

export const networkAvailabilitySaga = function*() {
  yield takeEvery(NETWORK_AVAILABILITY_INITIATED, function*({
    payload,
  }: NetworkAvailabilityInitiatedAction) {
    if (payload) {
      try {
        let response = yield clientEffect(
          client => client.getNetworkAvailability,
          payload.networks,
          defaultMovieMeta,
        );

        if (response.ok) {
          yield put(
            networkAvailabilitySuccess({
              items: response.data.data.map(ItemFactory.create),
            }),
          );
        }
      } catch (e) {
        yield put(networkAvailabilityFailed(e));
      }
    }
  });
};
