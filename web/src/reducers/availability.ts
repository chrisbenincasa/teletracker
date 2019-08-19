import { Availability } from '../types';
import { handleAction, flattenActions } from './utils';
import {
  UpcomingAvailabilitySuccessfulAction,
  AllAvailabilitySuccessfulAction,
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  ALL_AVAILABILITY_SUCCESSFUL,
} from '../actions/availability';

export interface AvailabilityState {
  offset: number;
  canFetchMore: boolean;
  availability: Availability[];
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
      return {
        ...state,
        upcoming: {
          offset: 0,
          canFetchMore: false, // TODO(christian) change this when we can page through
          availability: payload.upcoming,
        },
        expiring: {
          offset: 0,
          canFetchMore: false,
          availability: payload.expiring,
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
      return {
        ...state,
        recentlyAdded: {
          offset: 0,
          canFetchMore: false,
          availability: payload.recentlyAdded,
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
