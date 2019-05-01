import { ObjectMetadata } from "./external/themoviedb/Movie";

export interface List {
  id: number;
  name: string;
  things: Thing[];
}

export interface User {
  id: number;
  name: string;
  lists: List[];
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
}

export interface Network {
  id: number;
  name: string;
  slug: string;
  shortname?: string;
}