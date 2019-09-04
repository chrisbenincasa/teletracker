import { Availability } from '../types';
import { handleAction, flattenActions } from './utils';
import {
  UpcomingAvailabilitySuccessfulAction,
  AllAvailabilitySuccessfulAction,
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  ALL_AVAILABILITY_SUCCESSFUL,
} from '../actions/availability';
import Thing, { ThingFactory } from '../types/Thing';

export type DerivedAvailability = Omit<Availability, 'thing'> & {
  thing?: Thing;
};

export interface AvailabilityState {
  offset: number;
  canFetchMore: boolean;
  availability: DerivedAvailability[];
}

export interface State {
  upcoming?: AvailabilityState;
  expiring?: AvailabilityState;
  recentlyAdded?: AvailabilityState;
}

const initialState: State = {};

const upcomingExpiringSuccess = handleAction<
  UpcomingAvailabilitySuccessfulAction,
  State
>(
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  (state: State, { payload }: UpcomingAvailabilitySuccessfulAction) => {
    if (payload) {
      let derivedUpcoming = payload.upcoming.map(av => {
        return {
          ...av,
          thing: av.thing ? ThingFactory.create(av.thing) : undefined,
        } as DerivedAvailability;
      });

      let derivedExpiring = payload.expiring.map(av => {
        return {
          ...av,
          thing: av.thing ? ThingFactory.create(av.thing) : undefined,
        } as DerivedAvailability;
      });

      return {
        ...state,
        upcoming: {
          offset: 0,
          canFetchMore: false, // TODO(christian) change this when we can page through
          availability: derivedUpcoming,
        },
        expiring: {
          offset: 0,
          canFetchMore: false,
          availability: derivedExpiring,
        },
      };
    } else {
      return state;
    }
  },
);

const allAvailabilitySuccess = handleAction<
  AllAvailabilitySuccessfulAction,
  State
>(
  ALL_AVAILABILITY_SUCCESSFUL,
  (state: State, { payload }: AllAvailabilitySuccessfulAction) => {
    if (payload) {
      let derivedAdded = payload.recentlyAdded.map(av => {
        return {
          ...av,
          thing: av.thing ? ThingFactory.create(av.thing) : undefined,
        } as DerivedAvailability;
      });

      return {
        ...state,
        recentlyAdded: {
          offset: 0,
          canFetchMore: false,
          availability: derivedAdded,
        },
      };
    } else {
      return state;
    }
  },
);

export default flattenActions<State>(
  initialState,
  upcomingExpiringSuccess,
  allAvailabilitySuccess,
);
