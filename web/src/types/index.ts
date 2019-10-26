import Thing, { ApiThing } from './Thing';
import { ApiItem } from './v2';
import { Item } from './v2/Item';

export interface Paging {
  bookmark?: string;
}

export interface List {
  id: number;
  name: string;
  items: Item[];
  isDefault?: boolean;
  isDeleted?: boolean;
  isDynamic?: boolean;
  isPublic?: boolean;
  totalItems: number;
  configuration?: ListConfiguration;
}

export type ListSortOptions =
  | 'popularity'
  | 'recent'
  | 'added_time'
  | 'default';

export function isListSortOption(s: string): s is ListSortOptions {
  const allowed = ['popularity', 'recent', 'added_time', 'default'];
  return allowed.includes(s);
}

export type ItemType = 'movie' | 'show';

// TODO: There are better ways to do this... explore them.
export function isItemType(s: string): s is ItemType {
  const allowed = ['movie', 'show']; // Must match above
  return allowed.includes(s);
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

export interface ListConfiguration {
  ruleConfiguration?: ListRules;
  options?: ListOptions;
}

export interface ListOptions {
  removeWatchedItems: boolean;
}

export type ListRuleTypeKeys = 'TrackedListTagRule' | 'TrackedListPersonRule';
export type ListRuleTypes = ListTagRule | ListPersonRule;

export interface ListRules {
  rules: ListRuleTypes[];
}

export interface ListRule {
  type: ListRuleTypeKeys;
}

export interface ListTagRule extends ListRule {
  tagType: string;
  value?: number;
  isPresent?: boolean;
  type: 'TrackedListTagRule';
}

export interface ListPersonRule extends ListRule {
  personId: string;
  type: 'TrackedListPersonRule';
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
  thingId?: number;
  action: ActionType;
  value?: number;
}

export type PresentationType = 'sd' | 'hd' | '4k';

export interface UserPreferences {
  presentationTypes: PresentationType[];
  showOnlyNetworkSubscriptions: boolean;
}

export interface CastMember {
  id: string;
  name: string;
  slug: string;
  characterName?: string;
  relation?: string;
  tmdbId?: string;
  profilePath?: string;
}

export interface ThingUserMetadata {
  belongsToLists: List[];
  tags: UserThingTag[];
}

export interface Availability {
  id: number;
  isAvailable: boolean;
  region?: string;
  startDate?: string;
  offerType:
    | 'buy'
    | 'rent'
    | 'theater'
    | 'subscription'
    | 'free'
    | 'ads'
    | 'aggregate';
  cost?: number;
  currency?: string;
  thingId: number;
  networkId: number;
  network?: Network;
  presentationType?: PresentationType;
  thing?: ApiThing;
}
export interface Network {
  id: number;
  name: string;
  slug: string;
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
