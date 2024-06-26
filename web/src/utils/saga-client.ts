import { call } from '@redux-saga/core/effects';
import {
  ActionType,
  ItemType,
  ListOptions,
  ListRules,
  SortOptions,
  Network,
  NetworkType,
  OpenRange,
  UserPreferences,
} from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { TeletrackerApi } from './api-client';
import { CognitoUser } from 'amazon-cognito-identity-js';
import Auth from '@aws-amplify/auth';
import { Id, Slug } from '../types/v2';
import { FilterParams } from './searchFilters';
import { ItemSearchRequest } from '../types/client';

export class SagaTeletrackerClient {
  static instance = new SagaTeletrackerClient();

  *getUserSelf() {
    return yield this.apiCall(
      client => client.getUserSelf,
      yield call([this, this.withToken]),
    );
  }

  *updateUserSelf(
    networkSubscriptions: Network[] | undefined,
    preferences?: UserPreferences | undefined,
  ) {
    return yield this.apiCall(
      client => client.updateUserSelf,
      yield call([this, this.withToken]),
      networkSubscriptions,
      preferences,
    );
  }

  *getList(id: string) {
    return yield this.apiCall(
      client => client.getList,
      yield this.withToken(),
      id,
    );
  }

  *getListItems(id: string, request: ItemSearchRequest) {
    return yield this.apiCall(
      client => client.getListItems,
      yield this.withToken(),
      id,
      request,
    );
  }

  *updateList(
    id: string,
    name?: string,
    rules?: ListRules,
    options?: ListOptions,
  ) {
    return yield this.apiCall(
      client => client.updateList,
      yield call([this, this.withToken]),
      id,
      name,
      rules,
      options,
    );
  }

  *createList(name: string, itemIds?: string[], rules?: ListRules) {
    return yield this.apiCall(
      client => client.createList,
      yield call([this, this.withToken]),
      name,
      itemIds,
      rules,
    );
  }

  *deleteList(listId: string, mergeListId?: string) {
    return yield this.apiCall(
      client => client.deleteList,
      yield call([this, this.withToken]),
      listId,
      mergeListId,
    );
  }

  *renameList(listId: string, listName: string) {
    return yield this.apiCall(
      client => client.renameList,
      yield call([this, this.withToken]),
      listId,
      listName,
    );
  }

  *getLists(fields?: KeyMap<ObjectMetadata>, includeThings: boolean = false) {
    return yield this.apiCall(
      client => client.getLists,
      yield this.withToken(),
      fields,
      includeThings,
    );
  }

  *addItemToList(listId: string, itemId: string) {
    return yield this.apiCall(
      client => client.addItemToList,
      (yield call([this, this.withToken]))!,
      listId,
      itemId,
    );
  }

  *updateListTracking(
    itemId: string,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return yield this.apiCall(
      client => client.updateListTracking,
      yield call([this, this.withToken]),
      itemId,
      addToLists,
      removeFromLists,
    );
  }

  *getItem(id: string | number, type: string, includeRecommendations: boolean) {
    return yield this.apiCall(
      client => client.getItem,
      yield call([this, this.withToken]),
      id,
      type,
      includeRecommendations,
    );
  }

  *getItemRecommendations(id: string | number, type: string) {
    return yield this.apiCall(
      client => client.getItemRecommendations,
      yield call([this, this.withToken]),
      id,
      type,
    );
  }

  *getPerson(id: string) {
    return yield this.apiCall(
      client => client.getPerson,
      yield call([this, this.withToken]),
      id,
    );
  }

  *getPersonCredits(
    personId: Id | Slug,
    filterParams?: FilterParams,
    limit?: number,
    bookmark?: string,
    creditTypes?: string[],
  ) {
    return yield this.apiCall(
      client => client.getPersonCredits,
      yield call([this, this.withToken]),
      personId,
      filterParams,
      limit,
      bookmark,
      creditTypes,
    );
  }

  *getPeople(ids: (Id | Slug)[]) {
    return yield this.apiCall(
      client => client.getPeople,
      yield call([this, this.withToken]),
      ids,
    );
  }

  *updateActions(itemId: string, action: ActionType, value?: number) {
    return yield this.apiCall(
      client => client.updateActions,
      yield call([this, this.withToken]),
      itemId,
      action,
      value,
    );
  }

  *removeActions(itemId: string, action: ActionType) {
    return yield this.apiCall(
      client => client.removeActions,
      yield call([this, this.withToken]),
      itemId,
      action,
    );
  }

  *getUpcomingAvailability(
    networkIds?: number[],
    fields?: KeyMap<ObjectMetadata>,
  ) {
    return yield this.apiCall(
      client => client.getUpcomingAvailability,
      yield call([this, this.withToken]),
      networkIds,
      fields,
    );
  }

  *getAllAvailability(networkIds?: number[], fields?: KeyMap<ObjectMetadata>) {
    return yield this.apiCall(
      client => client.getAllAvailability,
      yield call([this, this.withToken]),
      networkIds,
      fields,
    );
  }

  *getItems(request: ItemSearchRequest) {
    let token = yield this.withToken();
    return yield this.apiCall(client => client.getItems, token, request);
  }

  *search(request: ItemSearchRequest) {
    return yield this.apiCall(
      client => client.search,
      yield call([this, this.withToken]),
      request,
    );
  }

  *searchPeople(searchText: string, limit?: number, bookmark?: string) {
    return yield this.apiCall(
      client => client.searchPeople,
      yield call([this, this.withToken]),
      searchText,
      limit,
      bookmark,
    );
  }

  *getNetworks() {
    return yield this.apiCall(
      client => client.getNetworks,
      yield call([this, this.withToken]),
    );
  }

  *getGenres() {
    return yield this.apiCall(client => client.getGenres);
  }

  *getMetadata() {
    return yield this.apiCall(client => client.getMetadata);
  }

  private apiCall<Fn extends (this: TeletrackerApi, ...args: any[]) => any>(
    fn: (clnt: TeletrackerApi) => Fn,
    ...args: Parameters<Fn>
  ) {
    return call(
      {
        context: TeletrackerApi.instance,
        fn: fn(TeletrackerApi.instance),
      },
      ...args,
    );
  }

  private *withToken() {
    let user: CognitoUser | null;
    try {
      user = yield call([Auth, Auth.currentAuthenticatedUser], {
        bypassCache: false,
      });
    } catch (e) {
      user = null;
    }

    return yield call(() =>
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined,
    );
  }
}

export default SagaTeletrackerClient.instance;
