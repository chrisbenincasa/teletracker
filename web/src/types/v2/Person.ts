import * as R from 'ramda';
import {
  ApiItem,
  ApiPersonCrewCredit,
  CanonicalId,
  Id,
  ItemExternalId,
  ItemImage,
  Slug,
} from '.';
import { getTmdbProfileImage } from '../../utils/image-helper';
import { HasSlug, Item, ItemFactory } from './Item';
import PagedResponse from './PagedResponse';
import _ from 'lodash';

export interface Person extends HasSlug {
  adult?: boolean;
  biography?: string;
  birthday?: string;
  cast_credits?: PagedResponse<PersonCastCredit>;
  cast_credit_ids?: PagedResponse<string>;
  crew_credits?: ApiPersonCrewCredit[];
  external_ids?: ItemExternalId[];
  deathday?: string;
  homepage?: string;
  id: Id;
  images?: ItemImage[];
  name: string;
  place_of_birth?: string;
  popularity?: number;
  slug?: Slug;

  // Compute fields
  profile_path?: string;
  canonical_id: CanonicalId;
  canonicalUrl: string;
  relativeUrl: string;
}

export interface PersonCastCredit {
  character?: string;
  id: string;
  title: string;
  type: string;
  slug: string;
  item?: Item;
}

export interface ApiPersonCastCredit {
  character?: string;
  id: string;
  title: string;
  type: string;
  slug: string;
  item?: ApiItem;
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
  id: Id;
  images?: ItemImage[];
  name: string;
  place_of_birth?: string;
  popularity?: number;
  slug?: Slug;
}

export class PersonFactory {
  static create(apiPerson: ApiPerson): Person {
    const personWithoutCredits = _.omit(apiPerson, 'cast_credits');
    return {
      ...personWithoutCredits,
      cast_credit_ids: apiPerson.cast_credits
        ? {
            data: apiPerson.cast_credits.data.map(credit => credit.id),
            paging: apiPerson.cast_credits.paging,
          }
        : undefined,
      profile_path: getTmdbProfileImage(apiPerson as ApiPerson),
      backdrop_path: undefined,
      poster_path: undefined,
      canonical_id: apiPerson.slug || apiPerson.id,
      canonicalUrl: '/person/[id]?id=' + apiPerson.slug,
      relativeUrl: '/person/' + apiPerson.slug,
    } as Person;
  }

  static merge(left: Person, right: Person) {
    return R.mergeDeepRight(left, right) as Person;
  }
}
