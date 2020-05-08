import { ItemType, OfferType, PresentationType } from '..';
import { ApiPerson } from './Person';

export type Slug = string;
export type Id = string;
export type CanonicalId = string;

export interface ApiItem {
  alternate_titles?: string[];
  title?: string;
  adult?: boolean;
  availability?: ItemAvailability[];
  cast?: ApiItemCastMember[];
  crew?: ItemCrewMember[];
  external_ids?: ItemExternalId[];
  genres?: ItemGenre[];
  id: Id;
  images?: ItemImage[];
  original_title: string;
  overview?: string;
  popularity?: number;
  ratings?: ItemRating[];
  recommendations?: ApiItem[];
  release_date?: string;
  release_dates?: ItemReleaseDate[];
  runtime?: number;
  slug: Slug;
  tags?: ApiItemTag[];
  type: ItemType;
}

export interface ItemAvailability {
  networkId: number;
  networkName?: string;
  offers: ItemAvailabilityOffer[];
}

export interface ItemAvailabilityOffer {
  region: string;
  startDate?: string;
  endDate?: string;
  offerType: OfferType;
  cost?: number;
  currency?: string;
  presentationType?: PresentationType;
  links?: ItemAvailabilityOfferLinks;
}

export interface ItemAvailabilityOfferLinks {
  web?: string;
}

export interface ApiItemCastMember {
  character?: string;
  id: Id;
  order: number;
  name: string;
  slug: Slug;
  person?: ApiPerson;
}

export interface ItemCrewMember {
  id: Id;
  order?: number;
  name: string;
  department?: string;
  job?: string;
  slug?: Slug;
}

export interface ApiPersonCrewCredit {
  id: Id;
  title: string;
  department?: string;
  job?: string;
  type: string;
  slug?: Slug;
}

export interface ItemExternalId {
  provider: string;
  id: string;
}

export interface ItemGenre {
  id: number;
  name: string;
}

export interface Video {
  country_code: string;
  language_code: string;
  name: string;
  provider_id: number;
  provider_shortname: string;
  provider_source_id: string;
  size: number;
  video_source: string;
  video_source_id: string;
  video_type: string;
}

export interface ApiItemTag {
  tag: string;
  value?: number;
  string_value?: string;
}

export interface ItemImage {
  provider_id: number;
  provider_shortname: string;
  id: string;
  image_type: string;
}

export interface ItemRating {
  provider_id: number;
  provider_shortname: string;
  vote_average: number;
  vote_count?: number;
  weighted_average?: number;
}

export interface ItemReleaseDate {
  country_code: string;
  release_date?: string;
  certification?: string;
}
