import { ApiPerson, Person, PersonFactory } from './v2/Person';
import * as networks from '../constants/networks';

export interface Paging {
  readonly bookmark?: string;
  readonly total?: number;
}

export interface ApiList {
  readonly id: string;
  readonly name: string;
  readonly isDefault?: boolean;
  readonly isDeleted?: boolean;
  readonly isDynamic?: boolean;
  readonly isPublic?: boolean;
  readonly totalItems: number;
  readonly configuration?: ListConfiguration;
  readonly relevantPeople?: ApiPerson[];
  readonly aliases?: string[];
  readonly ownedByRequester: boolean;
}

export interface List {
  readonly id: string;
  readonly name: string;
  readonly isDefault?: boolean;
  readonly isDeleted?: boolean;
  readonly isDynamic?: boolean;
  readonly isPublic?: boolean;
  readonly totalItems: number;
  readonly configuration?: ListConfiguration;
  readonly relevantPeople?: Person[];
  readonly legacyId?: number;
  readonly createdAt?: Date;
  readonly aliases?: string[];
  readonly ownedByRequester: boolean;
}

export class ListFactory {
  static create(list: ApiList): List {
    return {
      ...list,
      relevantPeople: list.relevantPeople
        ? list.relevantPeople.map(PersonFactory.create)
        : undefined,
    };
  }
}

export type SortOptions =
  | 'popularity'
  | 'recent'
  | 'added_time'
  | 'rating|imdb';

export function isListSortOption(s: string): s is SortOptions {
  const allowed: string[] = [
    'popularity',
    'recent',
    'added_time',
    'rating|imdb',
    'rating|tmdb',
  ];
  return allowed.includes(s);
}

export type ImageType = 'poster' | 'backdrop' | 'profile';

export type ItemType = 'movie' | 'show';

export enum ItemTypeEnum {
  Movie = 'movie',
  Show = 'show',
}

// TODO: There are better ways to do this... explore them.
export function isItemType(s: string): s is ItemType {
  const allowed = ['movie', 'show']; // Must match above
  return allowed.includes(s);
}

export function toItemTypeEnum(itemType: ItemType): ItemTypeEnum {
  if (itemType === 'movie') {
    return ItemTypeEnum.Movie;
  } else {
    return ItemTypeEnum.Show;
  }
}

export type NetworkType =
  | typeof networks.Netflix
  | typeof networks.Hbo
  // | 'hbo-now'
  | typeof networks.PrimeVideo
  | typeof networks.AmazonVideo
  | typeof networks.Hulu
  | typeof networks.DisneyPlus
  | typeof networks.HboMax
  | typeof networks.AppleTv;

export function isNetworkType(s: string): s is NetworkType {
  return networks.AllNetworks.includes(s);
}

export function isNetworkTypeOrAll(s: string): s is NetworkType | 'all' {
  return s === 'all' || isNetworkType(s);
}

export const networkToPrettyName: { readonly [K in NetworkType]: string } = {
  [networks.Netflix]: 'Netflix',
  [networks.Hbo]: 'HBO',
  // 'hbo-now': 'HBO',
  [networks.PrimeVideo]: 'Prime Video',
  [networks.AmazonVideo]: 'Amazon Video',
  [networks.Hulu]: 'Hulu',
  [networks.DisneyPlus]: 'Disney Plus',
  [networks.HboMax]: 'HBO Max',
  [networks.AppleTv]: 'Apple TV',
};

export const networkToColor: { readonly [K in NetworkType]?: string } = {
  [networks.Netflix]: '#000',
  // 'hbo-now': '#fff',
  [networks.AmazonVideo]: '#fff',
  [networks.PrimeVideo]: '#fff',
  [networks.Hulu]: '#1ce783',
  [networks.DisneyPlus]: '#1a1d29',
  [networks.HboMax]: '#fff',
  [networks.Hbo]: '#fff',
  [networks.AppleTv]: '#fff',
};

export interface ListConfiguration {
  readonly ruleConfiguration?: ListRules;
  readonly options?: ListOptions;
}

export interface ListOptions {
  readonly removeWatchedItems: boolean;
}

export enum ListRuleType {
  UserListTagRule = 'UserListTagRule',
  UserListPersonRule = 'UserListPersonRule',
  UserListGenreRule = 'UserListGenreRule',
  UserListItemTypeRule = 'UserListItemTypeRule',
  UserListNetworkRule = 'UserListNetworkRule',
  UserListReleaseYearRule = 'UserListReleaseYearRule',
}

export type ListRuleTypeKeys =
  | 'UserListTagRule'
  | 'UserListPersonRule'
  | 'UserListGenreRule'
  | 'UserListItemTypeRule'
  | 'UserListNetworkRule'
  | 'UserListReleaseYearRule';

export type ListRuleTypes =
  | ListTagRule
  | ListPersonRule
  | ListGenreRule
  | ListItemTypeRule
  | ListNetworkRule;

export interface ListRules {
  readonly rules: ListRule[];
  readonly sort?: ListDefaultSort;
}

export interface ListDefaultSort {
  readonly sort: SortOptions;
}

export interface ListRule {
  readonly type: ListRuleTypeKeys;
}

export interface ListTagRule extends ListRule {
  readonly tagType: string;
  readonly value?: number;
  readonly isPresent?: boolean;
  readonly type: 'UserListTagRule';
}

export interface ListPersonRule extends ListRule {
  readonly personId: string;
  readonly type: 'UserListPersonRule';
}

export interface ListGenreRule extends ListRule {
  readonly genreId: number;
  readonly type: 'UserListGenreRule';
}

export interface ListItemTypeRule extends ListRule {
  readonly itemType: ItemType;
  readonly type: 'UserListItemTypeRule';
}

export interface ListNetworkRule extends ListRule {
  readonly networkId: number;
  readonly type: 'UserListNetworkRule';
}

export interface ListReleaseYearRule extends ListRule {
  readonly minimum?: number;
  readonly maximum?: number;
  readonly type: 'UserListReleaseYearRule';
}

export function ruleIsType<T extends ListRule>(
  rule: ListRule,
  type: T['type'],
  // type: ListRuleTypeKeys,
): rule is T {
  return rule.type === type;
}

// export function isGenreRule(rule: ListRule): rule is ListGenreRule {
// return ruleIsType(rule, 'UserListGenreRule');
// }

export interface User {
  readonly id: number;
  readonly name: string;
  readonly email: string;
  readonly username: string;
  readonly lists: List[];
  readonly networkSubscriptions: Network[];
  readonly userPreferences: UserPreferences;
}

export interface UserDetails {
  readonly networkPreferences: Network[];
  readonly preferences: UserPreferences;
}

export interface UserThingTag {
  readonly id?: number;
  readonly userId?: number;
  readonly itemId?: number;
  readonly action: ActionType;
  readonly value?: number;
}

export type PresentationType = 'sd' | 'hd' | '4k';

export enum OfferType {
  buy = 'buy',
  rent = 'rent',
  theater = 'theater',
  subscription = 'subscription',
  free = 'free',
  ads = 'ads',
  aggregate = 'aggregate',
}

export function isOfferType(s: string): s is OfferType {
  for (let ot in OfferType) {
    if (s.toLowerCase() === ot) {
      return true;
    }
  }

  return false;
}

export interface UserPreferences {
  readonly presentationTypes: PresentationType[];
  readonly showOnlyNetworkSubscriptions: boolean;
}

// Transition type while we remove hbo-go and hbo-now references
// from the backend
export type StoredNetworkType = Exclude<NetworkType, 'hbo-go'> | 'hbo';

export interface Network {
  readonly id: number;
  readonly name: string;
  readonly slug: StoredNetworkType;
  readonly shortname?: string;
  readonly homepage?: string;
  readonly origin?: string;
}

export interface Genre {
  readonly id: number;
  readonly name: string;
  readonly type: ('movie' | 'tv')[];
  readonly slug: string;
}

export enum ActionType {
  Watched = 'watched',
  Enjoyed = 'enjoyed',
  TrackedInList = 'tracked_in_list',
}

export interface OpenRange {
  readonly min?: number;
  readonly max?: number;
}

export interface MetadataResponse {
  readonly genres: Genre[];
  readonly networks: Network[];
}
