import * as apisauce from 'apisauce';
import { merge } from 'ramda';
import { List, User, Network, ActionType } from '../types';
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
  url: 'http://localhost:3001',
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

    this.api.addRequestTransform(req => {
      if (this.token) {
        if (req.params) {
          Object.assign(req.params, {
            token: this.token,
          });
        } else {
          req.params = {
            token: this.token,
          };
        }
      }
    });
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

  async getAuthStatus(): Promise<apisauce.ApiResponse<any>> {
    return this.api.get('/api/v1/auth/status');
  }

  async getUser(id: string | number) {
    if (!this.token) {
      return Promise.reject(new Error('getUser requires a token to be set'));
    }

    return this.api.get<User>(
      `/api/v1/users/${id}`,
      {},
      { headers: this.authHeaders() },
    );
  }

  async getUserSelf() {
    if (!this.token) {
      return Promise.reject(new Error('getUser requires a token to be set'));
    }

    return this.api.get<DataResponse<User>>(
      '/api/v1/users/self',
      {},
      { headers: this.authHeaders() },
    );
  }

  async updateUserSelf(user: User) {
    return this.withTokenCheck(async () => {
      return this.api.put('/api/v1/users/self', { user });
    });
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

  async search(searchText: string) {
    return this.withTokenCheck(async () => {
      return this.api.get<Thing[]>('/api/v1/search', {
        query: searchText,
      });
    });
  }

  async createList(name: string) {
    return this.withTokenCheck(async () => {
      return this.api.post<DataResponse<{ id: number }>>(
        '/api/v1/users/self/lists',
        {
          name,
        },
      );
    });
  }

  async getLists(fields?: KeyMap<ObjectMetadata>) {
    let filterString = fields ? this.createFilter(fields) : '';
    let params = {};

    if (filterString) {
      params['fields'] = filterString;
    }

    return this.withTokenCheck(async () => {
      return this.api.get<DataResponse<User>>(
        '/api/v1/users/self/lists',
        params,
      );
    });
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

  async getList(id: string | number) {
    return this.withTokenCheck(async () => {
      return this.api.get<DataResponse<List>>(`/api/v1/users/self/lists/${id}`);
    });
  }

  async updateListTracking(
    thingId: number,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return this.withTokenCheck(async () => {
      return this.api.put<any>(`/api/v1/users/self/things/${thingId}/lists`, {
        addToLists,
        removeFromLists,
      });
    });
  }

  async addItemToList(listId: string, itemId: string) {
    if (!this.token) {
      return Promise.reject(new Error('getUser requires a token to be set'));
    }

    return this.api.put<any>(
      `/api/v1/users/self/lists/${listId}`,
      { itemId },
      { headers: this.authHeaders() },
    );
  }

  async postEvent(
    eventType: string,
    targetType: string,
    targetId: string,
    details: string,
  ) {
    this.withTokenCheck(async () => {
      return this.api.post<any>('/api/v1/users/self/events', {
        event: {
          type: eventType,
          targetEntityType: targetType,
          targetEntityId: targetId,
          timestamp: new Date().getTime(),
          details,
        },
      });
    });
  }

  async getItem(id: string | number, type: string) {
    return this.withTokenCheck(async () => {
      return this.api.get<any>(`/api/v1/${type}s/${id}`);
    });
  }

  async getShow(id: string | number) {
    return this.withTokenCheck(async () => {
      return this.api.get<any>(`/api/v1/shows/${id}`);
    });
  }

  async getMovie(id: string | number) {
    return this.withTokenCheck(async () => {
      return this.api.get<any>(`/api/v1/movies/${id}`);
    });
  }

  async getEvents() {
    return this.withTokenCheck(async () => {
      return this.api.get<any>('/api/v1/users/self/events');
    });
  }

  async getThingUserDetails(showId: string | number) {
    return this.withTokenCheck(async () => {
      return this.api.get<any>(`/api/v1/things/${showId}/user-details`);
    });
  }

  async getNetworks(): Promise<TeletrackerResponse<Network[]>> {
    return this.api.get<DataResponse<Network[]>>('/api/v1/networks');
  }

  async updateActions(thingId: number, action: ActionType, value?: number) {
    return this.withTokenCheck(async () => {
      return this.api.put(`/api/v1/users/self/things/${thingId}/actions`, {
        action,
        value,
      });
    });
  }

  async removeActions(thingId: number, action: ActionType) {
    return this.withTokenCheck(async () => {
      return this.api.delete(
        `/api/v1/users/self/things/${thingId}/actions/${action}`,
      );
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
