import { ItemType, OfferType, PresentationType } from '..';
import { ApiPerson } from './Person';

export type Slug = string;
export type Id = string;
export type CanonicalId = string;

export interface ApiItem {
  readonly alternate_titles?: string[];
  readonly title?: string;
  readonly adult?: boolean;
  readonly availability?: ItemAvailability[];
  readonly cast?: ApiItemCastMember[];
  readonly crew?: ItemCrewMember[];
  readonly external_ids?: ItemExternalId[];
  readonly genres?: ItemGenre[];
  readonly id: Id;
  readonly images?: ItemImage[];
  readonly original_title: string;
  readonly overview?: string;
  readonly popularity?: number;
  readonly ratings?: ItemRating[];
  readonly recommendations?: ApiItem[];
  readonly release_date?: string;
  readonly release_dates?: ItemReleaseDate[];
  readonly runtime?: number;
  readonly slug: Slug;
  readonly tags?: ApiItemTag[];
  readonly type: ItemType;
}

export interface ItemAvailability {
  readonly networkId: number;
  readonly networkName?: string;
  readonly offers: ItemAvailabilityOffer[];
}

export interface ItemAvailabilityOffer {
  readonly region: string;
  readonly startDate?: string;
  readonly endDate?: string;
  readonly offerType: OfferType;
  readonly cost?: number;
  readonly currency?: string;
  readonly presentationType?: PresentationType;
  readonly links?: ItemAvailabilityOfferLinks;
}

export interface ItemAvailabilityOfferLinks {
  readonly web?: string;
}

export interface ApiItemCastMember {
  readonly character?: string;
  readonly id: Id;
  readonly order: number;
  readonly name: string;
  readonly slug: Slug;
  readonly person?: ApiPerson;
}

export interface ItemCrewMember {
  readonly id: Id;
  readonly order?: number;
  readonly name: string;
  readonly department?: string;
  readonly job?: string;
  readonly slug?: Slug;
}

export interface ApiPersonCrewCredit {
  readonly id: Id;
  readonly title: string;
  readonly department?: string;
  readonly job?: string;
  readonly type: string;
  readonly slug?: Slug;
}

export interface ItemExternalId {
  readonly provider: string;
  readonly id: string;
}

export interface ItemGenre {
  readonly id: number;
  readonly name: string;
}

export interface Video {
  readonly country_code: string;
  readonly language_code: string;
  readonly name: string;
  readonly provider_id: number;
  readonly provider_shortname: string;
  readonly provider_source_id: string;
  readonly size: number;
  readonly video_source: string;
  readonly video_source_id: string;
  readonly video_type: string;
}

export interface ApiItemTag {
  readonly tag: string;
  readonly value?: number;
  readonly string_value?: string;
}

export interface ItemImage {
  readonly provider_id: number;
  readonly provider_shortname: string;
  readonly id: string;
  readonly image_type: string;
}

export interface ItemRating {
  readonly provider_id: number;
  readonly provider_shortname: string;
  readonly vote_average: number;
  readonly vote_count?: number;
  readonly weighted_average?: number;
}

export interface ItemReleaseDate {
  readonly country_code: string;
  readonly release_date?: string;
  readonly certification?: string;
}
