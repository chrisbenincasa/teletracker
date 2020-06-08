import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { AppThunk, RootState } from '../../app/store';
import {
  DeepReadonlyObject,
  PotentialMatch,
  PotentialMatchState,
} from '../../types';
import {
  getPotentialMatches,
  SearchMatchRequest,
  UpdateMatchRequest,
  updatePotentialMatch,
} from '../../util/apiClient';

type MatchingState = DeepReadonlyObject<{
  items: ReadonlyArray<PotentialMatch>;
  bookmark?: string;
  totalHits?: number;
  loading: {
    matches: boolean;
    updateMatch: boolean;
  };
}>;

type SearchMatchPayload = DeepReadonlyObject<{
  items: ReadonlyArray<PotentialMatch>;
  bookmark?: string;
  total?: number;
  append: boolean;
  state?: PotentialMatchState;
}>;

type UpdateMatchSuccessPayload = DeepReadonlyObject<{
  id: string;
  state: PotentialMatchState;
}>;

const initialState: MatchingState = {
  items: [],
  loading: {
    matches: false,
    updateMatch: false,
  },
};

export const matchingSlice = createSlice({
  name: 'matching',
  initialState,
  reducers: {
    getMatchesInitiated: (state) => {
      state.loading.matches = true;
    },
    getMatchesSuccess: (state, action: PayloadAction<SearchMatchPayload>) => {
      state.loading.matches = false;

      if (action.payload.append) {
        state.items = [...state.items, ...action.payload.items];
      } else {
        state.items = [...action.payload.items];
      }

      state.bookmark = action.payload.bookmark;
      state.totalHits = action.payload.total;
    },
    updateMatchInitiated: (state) => {
      state.loading.updateMatch = true;
    },
    updateMatchSuccess: (
      state,
      action: PayloadAction<UpdateMatchSuccessPayload>,
    ) => {
      state.loading.updateMatch = false;

      let updatedItem = state.items.find(
        (item) => item.id === action.payload.id,
      );

      if (updatedItem) {
        updatedItem.state = action.payload.state;
      }
    },
  },
});

export const {
  getMatchesInitiated,
  getMatchesSuccess,
  updateMatchInitiated,
  updateMatchSuccess,
} = matchingSlice.actions;

export const fetchMatchesAsync = (
  request: SearchMatchRequest = {},
): AppThunk => async (dispatch) => {
  dispatch(getMatchesInitiated());

  try {
    const response = await getPotentialMatches(request);

    if (response.status)
      dispatch(
        getMatchesSuccess({
          items: response.data.data,
          append: request.bookmark !== undefined,
          bookmark: response.data.paging?.bookmark,
          total: response.data.paging?.total,
          state: request.state,
        }),
      );
  } catch (e) {
    console.error(e);
  }
};

export const updatePotentialMatchAsync = (
  request: UpdateMatchRequest,
): AppThunk => async (dispatch) => {
  dispatch(updateMatchInitiated());

  try {
    await updatePotentialMatch(request);

    dispatch(
      updateMatchSuccess({
        id: request.id,
        state: request.state,
      }),
    );
  } catch (e) {
    console.error(e);
  }
};

export const selectMatchItems = (state: RootState) =>
  state.matching.items.filter(
    (item) => item.state === PotentialMatchState.Unmatched,
  );

export default matchingSlice.reducer;
