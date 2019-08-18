import { TeletrackerApi } from '../utils/api-client';
import { call, put, take, race, delay } from '@redux-saga/core/effects';
import { FSA } from 'flux-standard-action';
import { SET_TOKEN, TOKEN_SET } from '../constants/auth';
import client, { SagaTeletrackerClient } from '../utils/saga-client';

/**
 * Alternative to "createAction" where the generation action creator function takes
 * exactly 0 arguments. This exists because I couldn't figure out a sane way to generalize
 * this as a part of the createAction method
 * @param actiontype
 */
export function createBasicAction<T extends FSA<any>>(actiontype: T['type']) {
  return () => {
    return {
      type: actiontype,
    };
  };
}

/**
 * Generates a action creator method for the given Action type. The Action type
 * must be a Flux Standard Action type.
 * @param actionType
 */
export function createAction<T extends FSA<any, any>>(actionType: T['type']) {
  return (payload: T['payload']) => {
    return {
      type: actionType,
      payload: payload,
    };
  };
}

/**
 * Returns a redux effect that runs a function against the global
 * TeletrackerApi instance, correctly setting the "context" (this)
 * value on the call so the clalsite doesn't have to worry abou tit
 */
export function clientEffect<
  Fn extends (this: SagaTeletrackerClient, ...args: any[]) => any
>(fn: (clnt: SagaTeletrackerClient) => Fn, ...args: Parameters<Fn>) {
  return call(
    {
      context: client,
      fn: fn(client),
    },
    ...args,
  );
}

/**
 * Returns a redux effect that either times out after timeoutMs or completes
 * with the indication that the token has been set, permitting following effects
 * to safely access auth-gated information via the Teletracker API client.
 * @param timeoutMs
 */
export function checkOrSetToken(timeoutMs: number = 15000) {
  function* checkOrSetTokenImpl() {
    // Attempt to set the token
    // The saga for set token requests will either accept this and attempt to set
    // or do nothing (in the case that the token is already set OR a set request is IN PROGRESS)
    yield put({ type: SET_TOKEN });

    // After putting in the request to set the token, we wait on that saga to alert that the work
    // is done, since this all happens asynchronously
    yield take(TOKEN_SET);

    // Lastly, we just return true so callers can easily check that we hit
    // this case and not the timeout path.
    return true;
  }

  // Start 2 sagas at the same time. Either timeout after timeoutMs millis
  // or indicate that the token has been set on the client.
  return race({
    token: call(checkOrSetTokenImpl),
    timeout: delay(timeoutMs),
  });
}
