import * as apisauce from 'apisauce';
import _ from 'lodash';
import { merge } from 'ramda';
import {
  ActionType,
  Genre,
  ItemType,
  List,
  ListOptions,
  ListRules,
  SortOptions,
  Network,
  NetworkType,
  OpenRange,
  Paging,
  User,
  UserPreferences,
  MetadataResponse,
} from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { ApiThing } from '../types/Thing';
import { ApiItem, Id, Slug } from '../types/v2';
import { FilterParams } from './searchFilters';
import { ApiPerson } from '../types/v2/Person';

export interface TeletrackerApiOptions {
  url?: string;
  token?: string;
}

export interface DataResponse<T> {
  data: T;
  paging?: Paging;
}

export type TeletrackerResponse<T> = apisauce.ApiResponse<DataResponse<T>>;

const DefaultTeletrackerApiOptions: TeletrackerApiOptions = {
  url: process.env.REACT_APP_TELETRACKER_URL,
};

type ApiVersions = 'v1' | 'v2';
type SearchApiVersions = 'v2' | 'v3';

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

  async getUserSelf(token: string) {
    return this.api.get<DataResponse<User>>('/api/v2/users/self', { token });
  }

  async updateUserSelf(
    token: string,
    networkSubscriptions: Network[] | undefined,
    userPreferences?: UserPreferences | undefined,
  ) {
    return this.api.put(
      '/api/v2/users/self',
      { networkSubscriptions, userPreferences },
      { params: { token } },
    );
  }

  async search(token: string, searchText: string, bookmark?: string) {
    return this.api.get<ApiThing[]>('/api/v2/search', {
      query: searchText,
      token,
      bookmark,
    });
  }

  async searchV2(
    token: string | undefined,
    searchText: string,
    bookmark?: string,
    limit?: number,
    itemTypes?: ItemType[],
    networks?: NetworkType[],
    genres?: number[],
    releaseYearRange?: OpenRange,
    sort?: SortOptions | 'search_score',
  ): Promise<TeletrackerResponse<ApiItem[]>> {
    return this.api.get('/api/v3/search', {
      token,
      query: searchText,
      bookmark,
      limit,
      itemTypes,
      networks,
      genres,
      releaseYearRange,
      sort,
    });
  }

  async quickSearch(
    token: string,
    searchText: string,
    bookmark?: string,
    limit?: number,
    itemTypes?: ItemType[],
    networks?: NetworkType[],
    genres?: number[],
    releaseYearRange?: OpenRange,
    sort?: SortOptions,
  ) {
    return this.api.get<ApiItem[]>('/api/v3/search', {
      token,
      query: searchText,
      bookmark,
      limit,
      itemTypes,
      networks,
      genres,
      releaseYearRange,
      sort,
    });
  }

  async searchPeople(
    token: string,
    searchText: string,
    limit?: number,
    bookmark?: string,
  ) {
    return this.api.get<ApiPerson[]>('/api/v2/people/search', {
      query: searchText,
      token,
      limit,
      bookmark,
    });
  }

  async createList(
    token: string,
    name: string,
    thingIds?: string[],
    rules?: ListRules,
  ) {
    return this.api.post<DataResponse<{ id: number }>>(
      '/api/v2/users/self/lists',
      {
        name,
        thingIds,
        rules,
      },
      { params: { token } },
    );
  }

  async deleteList(token: string, listId: string, mergeListId?: string) {
    if (!mergeListId || mergeListId.length === 0) {
      return this.api.delete(`/api/v2/users/self/lists/${listId}`, { token });
    } else {
      return this.api.delete(
        `/api/v2/users/self/lists/${listId}?mergeWithList=${mergeListId}`,
        { token },
      );
    }
  }

  async renameList(token: string, listId: string, listName: string) {
    return this.api.put(
      `/api/v2/users/self/lists/${listId}`,
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

    return this.api.get<DataResponse<User>>('/api/v2/users/self/lists', params);
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

  async getList(
    token: string | undefined,
    id: string | number,
    sort?: SortOptions,
    desc?: boolean,
    itemTypes?: ItemType[],
    genres?: number[],
    bookmark?: string,
    networks?: NetworkType[],
    limit?: number,
  ) {
    return this.api.get<DataResponse<List>>(`/api/v2/lists/${id}/items`, {
      token,
      sort,
      desc,
      itemTypes: itemTypes ? itemTypes.join(',') : undefined,
      genres: genres ? genres.join(',') : undefined,
      bookmark,
      networks: networks ? networks.join(',') : undefined,
      limit,
    });
  }

  async getPublicList(
    token: string | undefined,
    id: string | number,
    sort?: SortOptions,
    desc?: boolean,
    itemTypes?: ItemType[],
    genres?: number[],
    bookmark?: string,
    networks?: NetworkType[],
    limit?: number,
  ) {
    return this.api.get<DataResponse<List>>(`/api/v2/lists/${id}/items`, {
      token,
      sort,
      desc,
      itemTypes: itemTypes ? itemTypes.join(',') : undefined,
      genres: genres ? genres.join(',') : undefined,
      bookmark,
      networks: networks ? networks.join(',') : undefined,
      limit,
    });
  }

  async updateList(
    token: string,
    id: string,
    name?: string,
    rules?: ListRules,
    options?: ListOptions,
  ) {
    return this.api.put(
      `/api/v2/users/self/lists/${id}`,
      {
        name,
        rules,
        options,
      },
      {
        params: {
          token,
        },
      },
    );
  }

  async updateListTracking(
    token: string,
    thingId: string,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return this.api.put<any>(
      `/api/v2/users/self/things/${thingId}/lists`,
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
      `/api/v2/users/self/lists/${listId}/things`,
      { itemId },
      { params: { token } },
    );
  }

  async getItem(token: string | undefined, id: string | number, type: string) {
    return this.api.get<any>(`/api/v2/items/${id}`, {
      token,
      thingType: type,
    });
  }

  async getPerson(
    token: string | undefined,
    id: string,
    creditsLimit?: number,
  ) {
    return this.api.get<any>(`/api/v2/people/${id}`, {
      token,
      creditsLimit,
    });
  }

  async getPersonCredits(
    token: String,
    id: Id | Slug,
    filterParams?: FilterParams,
    limit?: number,
    bookmark?: string,
  ) {
    const { itemTypes, networks, sortOrder, genresFilter, sliders } =
      filterParams || {};

    return this.api.get<any>(`/api/v2/people/${id}/credits`, {
      token,
      itemTypes:
        itemTypes && itemTypes.length ? itemTypes.join(',') : undefined,
      networks: networks && networks.length ? networks.join(',') : undefined,
      bookmark,
      sort: sortOrder,
      limit,
      genres:
        genresFilter && genresFilter.length
          ? genresFilter.join(',')
          : undefined,
      minReleaseYear:
        sliders && sliders.releaseYear && sliders.releaseYear.min
          ? sliders.releaseYear.min
          : undefined,
      maxReleaseYear:
        sliders && sliders.releaseYear && sliders.releaseYear.max
          ? sliders.releaseYear.max
          : undefined,
    });
  }

  async getPeople(
    token: string | undefined,
    ids: (Id | Slug)[],
  ): Promise<TeletrackerResponse<ApiPerson[]>> {
    return this.api.get<any>(`/api/v2/people/batch`, {
      token,
      personIds: ids.join(','),
    });
  }

  async getNetworks(token: string): Promise<TeletrackerResponse<Network[]>> {
    return this.api.get<DataResponse<Network[]>>('/api/v1/networks', { token });
  }

  async getGenres(): Promise<TeletrackerResponse<Genre[]>> {
    return this.api.get<DataResponse<Genre[]>>('/api/v1/genres');
  }

  async getMetadata(): Promise<TeletrackerResponse<MetadataResponse>> {
    return this.api.get<DataResponse<MetadataResponse>>('/api/v1/metadata');
  }

  async updateActions(
    token: string,
    thingId: string,
    action: ActionType,
    value?: number,
  ) {
    return this.api.put(
      `/api/v2/users/self/things/${thingId}/actions`,
      {
        action,
        value,
      },
      { params: { token } },
    );
  }

  async removeActions(token: string, thingId: string, action: ActionType) {
    return this.api.delete(
      `/api/v2/users/self/things/${thingId}/actions/${action}`,
      { token },
    );
  }

  async getUpcomingAvailability(
    token: string,
    networkIds?: number[],
    fields?: KeyMap<ObjectMetadata>,
  ) {
    return this.api.get('/api/v2/availability/upcoming', {
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

  async getPopular(
    token?: string,
    fields?: KeyMap<ObjectMetadata>,
    itemTypes?: ItemType[],
    networks?: NetworkType[],
    bookmark?: string,
    sort?: SortOptions,
    limit?: number,
    genres?: number[],
    releaseYearRange?: OpenRange,
    castIncludes?: string[],
  ): Promise<TeletrackerResponse<ApiItem[]>> {
    return this.api.get('/api/v2/popular', {
      token,
      fields: fields ? this.createFilter(fields!) : undefined,
      itemTypes:
        itemTypes && itemTypes.length ? itemTypes.join(',') : undefined,
      networks: networks && networks.length ? networks.join(',') : undefined,
      bookmark,
      sort,
      limit,
      genres: genres && genres.length ? genres.join(',') : undefined,
      minReleaseYear:
        releaseYearRange && releaseYearRange.min
          ? releaseYearRange.min
          : undefined,
      maxReleaseYear:
        releaseYearRange && releaseYearRange.max
          ? releaseYearRange.max
          : undefined,
      cast:
        castIncludes && castIncludes.length
          ? castIncludes.join(',')
          : undefined,
    });
  }

  async getItems(
    token?: string,
    itemTypes?: ItemType[],
    networks?: NetworkType[],
    bookmark?: string,
    sort?: SortOptions,
    limit?: number,
    genres?: number[],
    releaseYearRange?: OpenRange,
    cast?: string[],
  ): Promise<TeletrackerResponse<ApiItem[]>> {
    return this.api.get('/api/v2/explore', {
      token,
      itemTypes:
        itemTypes && itemTypes.length ? itemTypes.join(',') : undefined,
      networks: networks && networks.length ? networks.join(',') : undefined,
      bookmark,
      sort,
      limit,
      genres: genres && genres.length ? genres.join(',') : undefined,
      cast: cast && cast.length ? cast.join(',') : undefined,
      minReleaseYear:
        releaseYearRange && releaseYearRange.min
          ? releaseYearRange.min
          : undefined,
      maxReleaseYear:
        releaseYearRange && releaseYearRange.max
          ? releaseYearRange.max
          : undefined,
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
