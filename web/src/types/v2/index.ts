import { ItemType } from '..';
import PagedResponse from './PagedResponse';

export interface ApiItem {
  adult?: boolean;
  availability?: ItemAvailability[];
  cast?: ApiItemCastMember[];
  crew?: ItemCrewMember[];
  external_ids?: ItemExternalId[];
  genres?: ItemGenre[];
  id: string;
  images?: ItemImage[];
  original_title: string;
  overview?: string;
  popularity?: number;
  ratings?: ItemRating[];
  recommendations?: ApiItem[];
  release_date?: string;
  release_dates?: ItemReleaseDate[];
  runtime?: number;
  slug: string;
  tags?: ApiItemTag[];
  title: string[];
  type: ItemType;
}

export interface ApiPerson {
  adult?: boolean;
  biography?: string;
  birthday?: string;
  cast_credits?: PagedResponse<ApiPersonCastCredit>;
  crew_credits?: ApiPersonCrewCredit[];
  external_ids?: ItemExternalId[];
  deathday?: string;
  homepage?: string;
  id: string;
  images?: ItemImage[];
  name: string;
  place_of_birth?: string;
  popularity?: number;
  slug?: string;
}

export interface ItemAvailability {
  network_id: number;
  region: string;
  start_date?: string;
  end_date?: string;
  offer_type: string;
  cost?: number;
  currency?: string;
  presentation_type?: string;
}

export interface ApiItemCastMember {
  character?: string;
  id: string;
  order: number;
  name: string;
  slug: string;
  person?: ApiPerson;
}

export interface ApiPersonCastCredit {
  character?: string;
  id: string;
  title: string;
  type: string;
  slug: string;
  item?: ApiItem;
}

export interface ItemCrewMember {
  id: string;
  order?: number;
  name: string;
  department?: string;
  job?: string;
  slug: string;
}

export interface ApiPersonCrewCredit {
  id: string;
  title: string;
  department?: string;
  job?: string;
  type: string;
  slug: string;
}

export interface ItemExternalId {
  provider: string;
  id: string;
}

export interface ItemGenre {
  id: number;
  name: string;
}

export interface ApiItemTag {
  tag: string;
  value?: number;
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
}

export interface ItemReleaseDate {
  country_code: string;
  release_date?: string;
  certification?: string;
}
