import * as apisauce from 'apisauce';
import { merge } from 'ramda';
import { List, User, Network, ActionType, UserPreferences } from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { Thing } from '../types';
import _ from 'lodash';

export interface TeletrackerApiOptions {
  url?: string;
  token?: string;
}

export interface DataResponse<T> {
  data: T;
}

export type TeletrackerResponse<T> = apisauce.ApiResponse<DataResponse<T>>;

const DefaultTeletrackerApiOptions: TeletrackerApiOptions = {
  // url: "http://10.0.0.75:3000", //Config.TELETRACKER_API_URL,
  url: process.env.REACT_APP_TELETRACKER_URL,
};

export class TeletrackerApi {
  private api: apisauce.ApisauceInstance;
  private token: string | undefined;

  static instance = new TeletrackerApi();

  constructor(opts?: TeletrackerApiOptions) {
    let options = merge(DefaultTeletrackerApiOptions, opts || {});

    this.api = apisauce.create({
      baseURL: options.url,
    });

    this.token = options.token;
  }

  isTokenSet(): boolean {
    return !!this.token;
  }

  setToken(token: string) {
    this.token = token;
  }

  clearToken() {
    this.token = undefined;
  }

  async getAuthStatus(token: string): Promise<apisauce.ApiResponse<any>> {
    return this.api.get('/api/v1/auth/status', { token });
  }

  async getUser(token: string, id: string) {
    return this.api.get<User>(`/api/v1/users/${id}`, { token });
  }

  async getUserSelf(token: string) {
    return this.api.get<DataResponse<User>>('/api/v1/users/self', { token });
  }

  async updateUserSelf(
    token: string,
    networkSubscriptions: Network[] | undefined,
    userPreferences?: UserPreferences | undefined,
  ) {
    return this.api.put(
      '/api/v1/users/self',
      { networkSubscriptions, userPreferences },
      { params: { token } },
    );
  }

  async loginUser(email: string, password: string) {
    const data = { email, password };
    return this.api.post<any>('/api/v1/auth/login', data).then(response => {
      if (response.ok) {
        this.setToken(response.data.data.token);
      }

      return response;
    });
  }

  async logoutUser() {
    return this.withTokenCheck(async () => {
      return this.api.post<any>('/api/v1/auth/logout');
    });
  }

  async registerUser(username: string, email: string, password: string) {
    const data = { username, email, password, name: username };
    return this.api.post<any>('/api/v1/users', data).then(response => {
      if (response.ok) {
        this.setToken(response.data.data.token);
      }

      return response;
    });
  }

  async search(token: string, searchText: string) {
    return this.api.get<Thing[]>('/api/v1/search', {
      query: searchText,
      token,
    });
  }

  async createList(token: string, name: string) {
    return this.api.post<DataResponse<{ id: number }>>(
      '/api/v1/users/self/lists',
      {
        name,
      },
      { params: { token } },
    );
  }

  async deleteList(token: string, listId: number, mergeListId: number) {
    if (mergeListId === 0) {
      return this.api.delete(`/api/v1/users/self/lists/${listId}`, { token });
    } else {
      return this.api.delete(
        `/api/v1/users/self/lists/${listId}?mergeWithList=${mergeListId}`,
        { token },
      );
    }
  }

  async renameList(token: string, listId: number, listName: string) {
    return this.api.put(
      `/api/v1/users/self/lists/${listId}`,
      {
        name: listName,
      },
      {
        params: { token },
      },
    );
  }

  async getLists(
    token: string,
    fields?: KeyMap<ObjectMetadata>,
    includeThings: boolean = false,
  ) {
    let filterString = fields ? this.createFilter(fields) : '';
    let params = {
      token,
      includeThings,
    };

    if (filterString) {
      params['fields'] = filterString;
    }

    return this.api.get<DataResponse<User>>('/api/v1/users/self/lists', params);
  }

  private createFilter<T>(fields: KeyMap<T>): string {
    return Object.keys(fields)
      .map(key => {
        let value = fields[key];
        if (_.isObject(value)) {
          let subfilter = this.createFilter(value);
          return `${key}{${subfilter}}`;
        } else if (_.isBoolean(value) && Boolean(value)) {
          return key;
        } else {
          return '';
        }
      })
      .join(',');
  }

  async getList(token: string, id: string | number) {
    return this.api.get<DataResponse<List>>(`/api/v1/users/self/lists/${id}`, {
      token,
    });
  }

  async updateListTracking(
    token: string,
    thingId: string,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return this.api.put<any>(
      `/api/v1/users/self/things/${thingId}/lists`,
      {
        addToLists,
        removeFromLists,
      },
      {
        params: { token },
      },
    );
  }

  async addItemToList(token: string, listId: string, itemId: string) {
    return this.api.put<any>(
      `/api/v1/users/self/lists/${listId}/things`,
      { itemId },
      { params: { token } },
    );
  }

  async postEvent(
    token: string,
    eventType: string,
    targetType: string,
    targetId: string,
    details: string,
  ) {
    return this.api.post<any>(
      '/api/v1/users/self/events',
      {
        event: {
          type: eventType,
          targetEntityType: targetType,
          targetEntityId: targetId,
          timestamp: new Date().getTime(),
          details,
        },
      },
      {
        params: { token },
      },
    );
  }

  async getThingsBatch(
    token: string,
    ids: number[],
    fields?: KeyMap<ObjectMetadata>,
  ) {
    return this.api.get('/api/v1/things', {
      thingIds: ids,
      fields: fields ? this.createFilter(fields!) : undefined,
      token,
    });
  }

  async getItem(token: string, id: string | number, type: string) {
    return this.api.get<any>(`/api/v1/${type}s/${id}`, { token });
  }

  async getShow(token: string, id: string | number) {
    return this.api.get<any>(`/api/v1/shows/${id}`, { token });
  }

  async getMovie(token: string, id: string | number) {
    return this.api.get<any>(`/api/v1/movies/${id}`, { token });
  }

  async getEvents(token: string) {
    return this.api.get<any>('/api/v1/users/self/events', { token });
  }

  async getThingUserDetails(token: string, showId: string | number) {
    return this.api.get<any>(`/api/v1/things/${showId}/user-details`, {
      token,
    });
  }

  async getNetworks(token: string): Promise<TeletrackerResponse<Network[]>> {
    return this.api.get<DataResponse<Network[]>>('/api/v1/networks', { token });
  }

  async updateActions(
    token: string,
    thingId: string,
    action: ActionType,
    value?: number,
  ) {
    return this.api.put(
      `/api/v1/users/self/things/${thingId}/actions`,
      {
        action,
        value,
      },
      { params: { token } },
    );
  }

  async removeActions(token: string, thingId: string, action: ActionType) {
    return this.api.delete(
      `/api/v1/users/self/things/${thingId}/actions/${action}`,
      { token },
    );
  }

  async getUpcomingAvailability(
    token: string,
    networkIds?: number[],
    fields?: KeyMap<ObjectMetadata>,
  ) {
    return this.api.get('/api/v1/availability/upcoming', {
      networkIds,
      fields: fields ? this.createFilter(fields!) : undefined,
      token,
    });
  }

  async getAllAvailability(
    token: string,
    networkIds?: number[],
    fields?: KeyMap<ObjectMetadata>,
  ) {
    return this.api.get('/api/v1/availability/all', {
      networkIds,
      fields: fields ? this.createFilter(fields!) : undefined,
      token,
    });
  }

  private withTokenCheck<T>(f: () => Promise<T>): Promise<T> {
    if (!this.token) {
      return Promise.reject(new Error('function requires a token to be set'));
    } else {
      return f();
    }
  }

  private authHeaders() {
    return {
      Authorization: `Bearer ${this.token}`,
    };
  }
}

export default TeletrackerApi.instance;
