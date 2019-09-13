import { call } from '@redux-saga/core/effects';
import * as firebase from 'firebase/app';
import {
  ActionType,
  ItemTypes,
  ListOptions,
  ListRules,
  ListSortOptions,
  Network,
  UserPreferences,
} from '../types';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { TeletrackerApi } from './api-client';

export class SagaTeletrackerClient {
  static instance = new SagaTeletrackerClient();

  *getAuthStatus() {
    return yield this.apiCall(
      client => client.getAuthStatus,
      yield call([this, this.withToken]),
    );
  }

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

  *getList(
    id: number,
    sort?: ListSortOptions,
    desc?: boolean,
    itemTypes?: ItemTypes,
    genres?: number[],
    bookmark?: string,
  ) {
    return yield this.apiCall(
      client => client.getList,
      yield this.withToken(),
      id,
      sort,
      desc,
      itemTypes,
      genres,
      bookmark,
    );
  }

  *updateList(
    id: number,
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

  *createList(name: string) {
    return yield this.apiCall(
      client => client.createList,
      yield call([this, this.withToken]),
      name,
    );
  }

  *deleteList(listId: number, mergeListId?: number) {
    return yield this.apiCall(
      client => client.deleteList,
      yield call([this, this.withToken]),
      listId,
      mergeListId,
    );
  }

  *renameList(listId: number, listName: string) {
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
    thingId: string,
    addToLists: string[],
    removeFromLists: string[],
  ) {
    return yield this.apiCall(
      client => client.updateListTracking,
      yield call([this, this.withToken]),
      thingId,
      addToLists,
      removeFromLists,
    );
  }

  *getItem(id: string | number, type: string) {
    return yield this.apiCall(
      client => client.getItem,
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

  *getThingsBatch(ids: number[], fields?: KeyMap<ObjectMetadata>) {
    return yield this.apiCall(
      client => client.getThingsBatch,
      yield call([this, this.withToken]),
      ids,
      fields,
    );
  }

  *updateActions(thingId: string, action: ActionType, value?: number) {
    return yield this.apiCall(
      client => client.updateActions,
      yield call([this, this.withToken]),
      thingId,
      action,
      value,
    );
  }

  *removeActions(thingId: string, action: ActionType) {
    return yield this.apiCall(
      client => client.removeActions,
      yield call([this, this.withToken]),
      thingId,
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

  *getPopular(
    fields?: KeyMap<ObjectMetadata>,
    itemTypes?: ItemTypes,
    networks?: string,
    bookmark?: string,
    limit?: number,
  ) {
    let token = yield this.withToken();
    return yield this.apiCall(
      client => client.getPopular,
      token,
      fields,
      itemTypes,
      networks,
      bookmark,
      limit,
    );
  }

  *getPopularGenre(genre: string, typeRestrict?: ItemTypes, bookmark?: string) {
    return yield this.apiCall(
      client => client.getPopularGenre,
      genre,
      typeRestrict,
      bookmark,
    );
  }

  *search(searchText: string, bookmark?: string) {
    return yield this.apiCall(
      client => client.search,
      yield call([this, this.withToken]),
      searchText,
      bookmark,
    );
  }

  *searchV2(searchText: string, bookmark?: string) {
    return yield this.apiCall(
      client => client.searchV2,
      yield call([this, this.withToken]),
      searchText,
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
    let user: firebase.User | undefined = this.getUser();
    return yield call(() => (user ? user.getIdToken() : undefined));
  }

  private getUser() {
    let user = firebase.auth().currentUser;
    return user ? user : undefined;
  }
}

export default SagaTeletrackerClient.instance;
