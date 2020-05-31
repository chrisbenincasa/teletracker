import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import axios from 'axios';
import { AppThunk, RootState } from '../../app/store';
import { PotentialMatch, DeepReadonlyObject } from '../../types';

interface MatchingState {
  readonly items: ReadonlyArray<PotentialMatch>;
  readonly bookmark?: string;
  readonly totalHits?: number;
}

interface SearchMatchRequest {
  bookmark?: string;
}

interface SearchMatchResponse {
  readonly data: ReadonlyArray<PotentialMatch>;
  readonly paging?: Readonly<{
    bookmark?: string;
    total?: number;
  }>;
}

type SearchMatchPayload = DeepReadonlyObject<{
  items: ReadonlyArray<PotentialMatch>;
  bookmark?: string;
  total?: number;
  append: boolean;
}>;

const initialState: MatchingState = {
  items: [],
};

export const matchingSlice = createSlice({
  name: 'matching',
  initialState,
  reducers: {
    getMatchesInitiated: (state) => {
      console.log('matches');
    },
    getMatchesSuccess: (state, action: PayloadAction<SearchMatchPayload>) => {
      if (action.payload.append) {
      } else {
        state.items = [...action.payload.items];
        state.bookmark = action.payload.bookmark;
        state.totalHits = action.payload.total;
      }
    },
  },
});

export const { getMatchesInitiated, getMatchesSuccess } = matchingSlice.actions;

export const fetchMatches = (
  request: SearchMatchRequest = {},
): AppThunk => async (dispatch) => {
  dispatch(getMatchesInitiated());
  try {
    const response = await axios.get<SearchMatchResponse>(
      `https://${process.env.REACT_APP_API_HOST}/api/v1/internal/potential_matches/search`,
      {
        params: {
          admin_key: process.env.REACT_APP_ADMIN_KEY,
          bookmark: request.bookmark,
        },
      },
    );

    dispatch(
      getMatchesSuccess({
        items: response.data.data,
        append: request.bookmark !== undefined,
        bookmark: response.data.paging?.bookmark,
        total: response.data.paging?.total,
      }),
    );
  } catch (e) {
    console.error(e);
  }
};

export const selectMatchItems = (state: RootState) => state.matching.items;

export default matchingSlice.reducer;
