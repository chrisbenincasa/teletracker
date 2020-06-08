import axios from 'axios';
import {
  DeepReadonlyObject,
  PotentialMatch,
  PotentialMatchState,
} from '../types';

const instance = axios.create({
  baseURL: `https://${process.env.REACT_APP_API_HOST}/api/v1/internal`,
});

export type SearchMatchRequest = DeepReadonlyObject<{
  bookmark?: string;
  state?: PotentialMatchState;
}>;

export type UpdateMatchRequest = DeepReadonlyObject<{
  id: string;
  state: PotentialMatchState;
}>;

export type SearchMatchResponse = DeepReadonlyObject<{
  readonly data: ReadonlyArray<PotentialMatch>;
  readonly paging?: Readonly<{
    bookmark?: string;
    total?: number;
  }>;
}>;

export const getPotentialMatches = async (request: SearchMatchRequest) => {
  return instance.get<SearchMatchResponse>(`/potential_matches/search`, {
    params: {
      admin_key: process.env.REACT_APP_ADMIN_KEY,
      bookmark: request.bookmark,
    },
  });
};

export const updatePotentialMatch = async (request: UpdateMatchRequest) => {
  return instance.put(
    `/potential_matches/${request.id}`,
    {
      status: request.state,
    },
    {
      params: {
        admin_key: process.env.REACT_APP_ADMIN_KEY,
      },
    },
  );
};
