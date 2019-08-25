import { ObjectMetadata } from './external/themoviedb/Movie';

export interface List {
  id: number;
  name: string;
  things: Thing[];
  isDefault?: boolean;
  isDeleted?: boolean;
  isDynamic?: boolean;
  isPublic?: boolean;
  thingCount: number;
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

export interface Thing {
  id: string;
  name: string;
  normalizedName: string;
  type: 'movie' | 'show' | 'person';
  metadata?: ObjectMetadata;
  userMetadata?: ThingUserMetadata;
  availability: Availability[];
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
  thing?: Thing;
}
export interface Network {
  id: number;
  name: string;
  slug: string;
  shortname?: string;
  homepage?: string;
  origin?: string;
}

export enum ActionType {
  Watched = 'watched',
  Enjoyed = 'enjoyed',
}
