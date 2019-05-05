import { ObjectMetadata } from './external/themoviedb/Movie';

export interface List {
  id: number;
  name: string;
  things: Thing[];
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

export type PresentationType = 'sd' | 'hd' | '4k';

export interface UserPreferences {
  presentationTypes: PresentationType[];
  showOnlyNetworkSubscriptions: boolean;
}

export interface Thing {
  id: string | number;
  name: string;
  normalizedName: string;
  type: 'movie' | 'show' | 'person';
  metadata?: ObjectMetadata;
  userMetadata?: ObjectMetadata;
  availability: Availability[];
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
}

export interface Network {
  id: number;
  name: string;
  slug: string;
  shortname?: string;
  homepage?: string;
  origin?: string;
}
