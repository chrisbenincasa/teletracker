import * as apisauce from 'apisauce';
import _ from 'lodash';
import { merge } from 'ramda';
import {
  ActionType,
  ApiList,
  Genre,
  ItemType,
  List,
  ListOptions,
  ListRules,
  MetadataResponse,
  Network,
  NetworkType,
  OpenRange,
  Paging,
  SortOptions,
  User,
  UserPreferences,
} from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { ApiItem, Id, Slug } from '../types/v2';
import { FilterParams } from './searchFilters';
import { ApiPerson } from '../types/v2/Person';
import { ItemSearchRequest } from '../types/client';

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

  async search(
    token: string | undefined,
    request: ItemSearchRequest,
  ): Promise<TeletrackerResponse<ApiItem[]>> {
    return this.api.get('/api/v3/search', {
      token,
      ...this.searchRequestToParams(request),
    });
  }

  async quickSearch(
    token: string | undefined,
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
    itemIds?: string[],
    rules?: ListRules,
  ) {
    return this.api.post<DataResponse<{ id: number }>>(
      '/api/v2/users/self/lists',
      {
        name,
        itemIds,
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

  async getList(token: string | undefined, id: string) {
    return this.api.get<DataResponse<ApiList>>(`/api/v2/lists/${id}`, {
      token,
    });
  }

  async getListItems(
    token: string | undefined,
    id: string,
    request: ItemSearchRequest,
  ) {
    return this.api.get<DataResponse<ApiItem[]>>(`/api/v2/lists/${id}/items`, {
      token,
      ...this.searchRequestToParams(request),
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
    itemId: string,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return this.api.put<any>(
      `/api/v2/users/self/things/${itemId}/lists`,
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

  async getItem(
    token: string | undefined,
    id: string | number,
    type: string,
    includeRecommendations: boolean,
  ) {
    return this.api.get<any>(`/api/v2/items/${id}`, {
      token,
      thingType: type,
      includeRecommendations,
    });
  }

  async getItemRecommendations(
    token: string | undefined,
    id: string | number,
    type: string,
  ) {
    return this.api.get<any>(`/api/v2/items/${id}/recommendations`, {
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
    token: string | undefined,
    id: Id | Slug,
    filterParams?: FilterParams,
    limit?: number,
    bookmark?: string,
    creditTypes?: string[],
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
      creditTypes:
        creditTypes && creditTypes.length > 0
          ? creditTypes.join(',')
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
    itemId: string,
    action: ActionType,
    value?: number,
  ) {
    return this.api.put(
      `/api/v2/users/self/things/${itemId}/actions`,
      {
        action,
        value,
      },
      { params: { token } },
    );
  }

  async removeActions(token: string, itemId: string, action: ActionType) {
    return this.api.delete(
      `/api/v2/users/self/things/${itemId}/actions/${action}`,
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

  async getItems(
    token: string | undefined,
    request: ItemSearchRequest,
  ): Promise<TeletrackerResponse<ApiItem[]>> {
    return this.api.get('/api/v2/explore', {
      token,
      ...this.searchRequestToParams(request),
    });
  }

  private searchRequestToParams(request: ItemSearchRequest) {
    let imdbMin = request.imdbRating?.min || '';
    let imdbMax = request.imdbRating?.max || '';
    let imdbPart: string | undefined;
    if (imdbMin || imdbMax) {
      imdbPart = `${imdbMin}:${imdbMax}`;
    }

    return {
      query: request.searchText,
      itemTypes:
        request.itemTypes && request.itemTypes.length
          ? request.itemTypes.join(',')
          : undefined,
      networks:
        request.networks && request.networks.length
          ? request.networks.join(',')
          : undefined,
      bookmark: request.bookmark,
      sort: request.sort,
      limit: request.limit,
      genres:
        request.genres && request.genres.length
          ? request.genres.join(',')
          : undefined,
      minReleaseYear:
        request.releaseYearRange && request.releaseYearRange.min
          ? request.releaseYearRange.min
          : undefined,
      maxReleaseYear:
        request.releaseYearRange && request.releaseYearRange.max
          ? request.releaseYearRange.max
          : undefined,
      cast:
        request.castIncludes && request.castIncludes.length
          ? request.castIncludes.join(',')
          : undefined,
      imdbRating: imdbPart,
    };
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
