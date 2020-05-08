import { ApiItem } from './v2';
import { Item, ItemFactory } from './v2/Item';
import { ApiPerson, Person, PersonFactory } from './v2/Person';

export interface Paging {
  bookmark?: string;
}

export interface ApiList {
  id: string;
  name: string;
  items?: ApiItem[];
  isDefault?: boolean;
  isDeleted?: boolean;
  isDynamic?: boolean;
  isPublic?: boolean;
  totalItems: number;
  configuration?: ListConfiguration;
  relevantPeople?: ApiPerson[];
  aliases?: string[];
  ownedByRequester: boolean;
}

export interface List {
  id: string;
  name: string;
  items?: Item[];
  isDefault?: boolean;
  isDeleted?: boolean;
  isDynamic?: boolean;
  isPublic?: boolean;
  totalItems: number;
  configuration?: ListConfiguration;
  relevantPeople?: Person[];
  legacyId?: number;
  createdAt?: Date;
  aliases?: string[];
  ownedByRequester: boolean;
}

export class ListFactory {
  static create(list: ApiList): List {
    return {
      ...list,
      items: (list.items || []).map(ItemFactory.create),
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
  | 'netflix'
  | 'netflix-kids'
  | 'hbo-go'
  | 'hbo-now'
  | 'amazon-prime-video'
  | 'amazon-video'
  | 'hulu';

export function isNetworkType(s: string): s is NetworkType {
  const allowed = [
    'netflix',
    'netflix-kids',
    'hbo-go',
    'hbo-now',
    'amazon-prime-video',
    'amazon-video',
    'hulu',
  ];

  return allowed.includes(s);
}

export const networkToPrettyName: { [K in NetworkType]?: string } = {
  netflix: 'Netflix',
  'netflix-kids': 'Netflix Kids',
  'hbo-go': 'HBO Go',
  'hbo-now': 'HBO Now',
  'amazon-prime-video': 'Prime Video',
  'amazon-video': 'Amazon Video',
  hulu: 'Hulu',
};

export interface ListConfiguration {
  ruleConfiguration?: ListRules;
  options?: ListOptions;
}

export interface ListOptions {
  removeWatchedItems: boolean;
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
  rules: ListRule[];
  sort?: ListDefaultSort;
}

export interface ListDefaultSort {
  sort: SortOptions;
}

export interface ListRule {
  type: ListRuleTypeKeys;
}

export interface ListTagRule extends ListRule {
  tagType: string;
  value?: number;
  isPresent?: boolean;
  type: 'UserListTagRule';
}

export interface ListPersonRule extends ListRule {
  personId: string;
  type: 'UserListPersonRule';
}

export interface ListGenreRule extends ListRule {
  genreId: number;
  type: 'UserListGenreRule';
}

export interface ListItemTypeRule extends ListRule {
  itemType: ItemType;
  type: 'UserListItemTypeRule';
}

export interface ListNetworkRule extends ListRule {
  networkId: number;
  type: 'UserListNetworkRule';
}

export interface ListReleaseYearRule extends ListRule {
  minimum?: number;
  maximum?: number;
  type: 'UserListReleaseYearRule';
}

export function ruleIsType<T extends ListRule>(
  rule: ListRule,
  type: ListRuleTypeKeys,
): rule is T {
  return rule.type === type;
}

export function isGenreRule(rule: ListRule): rule is ListGenreRule {
  return ruleIsType(rule, 'UserListGenreRule');
}

export interface User {
  id: number;
  name: string;
  email: string;
  username: string;
  lists: List[];
  networkSubscriptions: Network[];
  userPreferences: UserPreferences;
}

export interface UserDetails {
  networkPreferences: Network[];
  preferences: UserPreferences;
}

export interface UserThingTag {
  id?: number;
  userId?: number;
  itemId?: number;
  action: ActionType;
  value?: number;
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

export interface UserPreferences {
  presentationTypes: PresentationType[];
  showOnlyNetworkSubscriptions: boolean;
}

export interface Network {
  id: number;
  name: string;
  slug: NetworkType;
  shortname?: string;
  homepage?: string;
  origin?: string;
}

export interface Genre {
  id: number;
  name: string;
  type: ('movie' | 'tv')[];
  slug: string;
}

export enum ActionType {
  Watched = 'watched',
  Enjoyed = 'enjoyed',
  TrackedInList = 'tracked_in_list',
}

export interface OpenRange {
  min?: number;
  max?: number;
}

export interface MetadataResponse {
  genres: Genre[];
  networks: Network[];
}
