import { FSA } from 'flux-standard-action';

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
export const createAction = <T extends FSA<any, any>>(
  actionType: T['type'],
) => {
  return (payload: T['payload']) => {
    return {
      type: actionType,
      payload: payload,
    };
  };
};

export function withPayloadType<T>() {
  return (t: T) => ({ payload: t });
}

export function isServer() {
  return typeof window === 'undefined';
}
