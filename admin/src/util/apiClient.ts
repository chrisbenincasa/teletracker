import axios from 'axios';
import {
  DeepReadonly,
  DeepReadonlyObject,
  PotentialMatch,
  PotentialMatchState,
  ScrapeItemType,
} from '../types';

const instance = axios.create({
  baseURL: `${process.env.REACT_APP_API_SCHEME}://${process.env.REACT_APP_API_HOST}/api/v1/internal`,
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

export type GetTaskRequest = DeepReadonly<{
  id: string;
}>;

export type SearchTasksRequest = DeepReadonly<{
  sort?: string;
  desc?: boolean;
  limit?: number;
  taskName?: string;
  status?: TaskStatus[];
}>;

export type SearchTaskResponse = DeepReadonly<{
  data: Task[];
}>;

export type GetTaskResponse = DeepReadonly<{
  data: Task;
}>;

export enum TaskStatus {
  Waiting = 'waiting',
  Scheduled = 'scheduled',
  Executing = 'executing',
  Completed = 'completed',
  Failed = 'failed',
  Canceled = 'canceled',
}

export type Task = {
  id: string;
  taskName: string;
  status: TaskStatus;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
  hostname?: string;
  args?: object;
  logUri?: string;
};

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

export const getTasks = async (request: SearchTasksRequest) => {
  return instance.get<SearchTaskResponse>('/tasks', {
    params: {
      admin_key: process.env.REACT_APP_ADMIN_KEY,
      ...request,
      status:
        request.status && request.status.length > 0
          ? request.status.join(',')
          : undefined,
    },
  });
};

export const getTask = async (request: GetTaskRequest) => {
  return instance.get<GetTaskResponse>('/tasks/' + request.id, {
    params: {
      admin_key: process.env.REACT_APP_ADMIN_KEY,
    },
  });
};
