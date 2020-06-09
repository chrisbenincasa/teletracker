import axios from 'axios';
import {
  DeepReadonlyObject,
  PotentialMatch,
  PotentialMatchState,
  ScrapeItemType,
} from '../types';

const instance = axios.create({
  baseURL: `https://${process.env.REACT_APP_API_HOST}/api/v1/internal`,
});

export enum SearchMatchSort {
  Id = 'id',
  LastStateChange = 'last_state_change',
  PotentialMatchPopularity = 'potential.popularity',
}

export enum SupportedNetwork {
  Netflix = 'netflix',
  Hulu = 'hulu',
  Hbo = 'hbo',
  HboMax = 'hbo-max',
  DisneyPlus = 'disney-plus',
}

export type SearchMatchRequest = DeepReadonlyObject<{
  bookmark?: string;
  matchState?: PotentialMatchState;
  scraperItemType?: ScrapeItemType;
  networks?: SupportedNetwork[];
  limit?: number;
  sort?: SearchMatchSort;
  desc?: boolean;
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
      ...request,
      networks: request.networks ? request.networks.join(',') : undefined,
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
