import { call } from '@redux-saga/core/effects';
import client, { SagaTeletrackerClient } from '../utils/saga-client';
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
