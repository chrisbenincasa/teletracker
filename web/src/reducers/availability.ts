import { flattenActions, handleAction } from './utils';
import {
  ALL_AVAILABILITY_SUCCESSFUL,
  AllAvailabilitySuccessfulAction,
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  UpcomingAvailabilitySuccessfulAction,
} from '../actions/availability';
import { Item } from '../types/v2/Item';
import { List, Record, RecordOf } from 'immutable';

export type AvailabilityState = {
  offset: number;
  canFetchMore: boolean;
  availability: List<Item>;
};

const makeAvailabilityState = Record<AvailabilityState>({
  offset: 0,
  canFetchMore: true,
  availability: List.of<Item>(),
});

export type StateType = {
  upcoming?: RecordOf<AvailabilityState>;
  expiring?: RecordOf<AvailabilityState>;
  recentlyAdded?: RecordOf<AvailabilityState>;
};

export type State = RecordOf<StateType>;

const initialState: StateType = {};

export const makeState: Record.Factory<StateType> = Record(initialState);

const upcomingExpiringSuccess = handleAction<
  UpcomingAvailabilitySuccessfulAction,
  State
>(
  UPCOMING_AVAILABILITY_SUCCESSFUL,
  (state: State, { payload }: UpcomingAvailabilitySuccessfulAction) => {
    if (payload) {
      return state.merge({
        upcoming: (state.upcoming || makeAvailabilityState()).merge({
          offset: 0,
          canFetchMore: false, // TODO(christian) change this when we can page through
          availability: List(payload!.upcoming),
        }),
        expiring: (state.expiring || makeAvailabilityState()).merge({
          offset: 0,
          canFetchMore: false,
          availability: List(payload!.expiring),
        }),
      });
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
      return state.set(
        'recentlyAdded',
        (state.recentlyAdded || makeAvailabilityState()).merge({
          offset: 0,
          canFetchMore: false,
          availability: List(payload!.recentlyAdded),
        }),
      );
    } else {
      return state;
    }
  },
);

export default {
  initialState: makeState(),
  reducer: flattenActions<State>(
    'availability',
    makeState(),
    upcomingExpiringSuccess,
    allAvailabilitySuccess,
  ),
};
