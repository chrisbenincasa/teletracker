import { call, take } from '@redux-saga/core/effects';
import * as firebase from 'firebase/app';
import { TeletrackerApi } from './api-client';
import { KeyMap, ObjectMetadata } from '../types/external/themoviedb/Movie';
import { ActionType, Network, User, UserPreferences } from '../types';

export class SagaTeletrackerClient {
  static instance = new SagaTeletrackerClient();

  *getAuthStatus() {
    return yield this.apiCall(
      client => client.getAuthStatus,
      yield this.withToken(),
    );
  }

  *getUserSelf() {
    return yield this.apiCall(
      client => client.getUserSelf,
      yield this.withToken(),
    );
  }

  *updateUserSelf(
    networkSubscriptions: Network[] | undefined,
    preferences?: UserPreferences | undefined,
  ) {
    return yield this.apiCall(
      client => client.updateUserSelf,
      yield this.withToken(),
      networkSubscriptions,
      preferences,
    );
  }

  *getList(id: number) {
    return yield this.apiCall(
      client => client.getList,
      yield this.withToken(),
      id,
    );
  }

  *createList(name: string) {
    return yield this.apiCall(
      client => client.createList,
      yield this.withToken(),
      name,
    );
  }

  *deleteList(listId: number, mergeListId?: number) {
    return yield this.apiCall(
      client => client.deleteList,
      yield this.withToken(),
      listId,
      mergeListId,
    );
  }

  *renameList(listId: number, listName: string) {
    return yield this.apiCall(
      client => client.renameList,
      yield this.withToken(),
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
      yield this.withToken(),
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
      yield this.withToken(),
      thingId,
      addToLists,
      removeFromLists,
    );
  }

  *getItem(id: string | number, type: string) {
    return yield this.apiCall(
      client => client.getItem,
      yield this.withToken(),
      id,
      type,
    );
  }

  *getThingsBatch(ids: number[], fields?: KeyMap<ObjectMetadata>) {
    return yield this.apiCall(
      client => client.getThingsBatch,
      yield this.withToken(),
      ids,
      fields,
    );
  }

  *updateActions(thingId: string, action: ActionType, value?: number) {
    return yield this.apiCall(
      client => client.updateActions,
      yield this.withToken(),
      thingId,
      action,
      value,
    );
  }

  *removeActions(thingId: string, action: ActionType) {
    return yield this.apiCall(
      client => client.removeActions,
      yield this.withToken(),
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
      yield this.withToken(),
      networkIds,
      fields,
    );
  }

  *getAllAvailability(networkIds?: number[], fields?: KeyMap<ObjectMetadata>) {
    return yield this.apiCall(
      client => client.getAllAvailability,
      yield this.withToken(),
      networkIds,
      fields,
    );
  }

  *getPopular(networkIds?: number[], fields?: KeyMap<ObjectMetadata>) {
    return yield this.apiCall(
      client => client.getPopular,
      yield this.withToken(),
      networkIds,
      fields,
    );
  }

  *search(searchText: string) {
    return yield this.apiCall(
      client => client.search,
      yield this.withToken(),
      searchText,
    );
  }

  *getNetworks() {
    return yield this.apiCall(
      client => client.getNetworks,
      yield this.withToken(),
    );
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
    let user = yield this.getUser();
    let token: string = yield this.getTokenEffect(user!);
    return token;
  }

  private *getUser() {
    let user = firebase.auth().currentUser;
    while (!user) {
      let { payload } = yield take('USER_STATE_CHANGE');
      user = payload;
    }
    return user;
  }

  private getTokenEffect(user: firebase.User) {
    return call(() => user.getIdToken());
  }
}

export default SagaTeletrackerClient.instance;
