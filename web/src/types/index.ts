import Thing, { ApiThing } from './Thing';

export interface CommonList {
  id: number;
  name: string;
  isDefault?: boolean;
  isDeleted?: boolean;
  isDynamic?: boolean;
  isPublic?: boolean;
  thingCount: number;
  configuration?: ListConfiguration;
}

export interface APIList extends CommonList {
  things: ApiThing[];
}

export interface List extends CommonList {
  things: Thing[];
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
  type: string;
  slug: string;
}

export enum ActionType {
  Watched = 'watched',
  Enjoyed = 'enjoyed',
}
