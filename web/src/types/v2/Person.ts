import * as R from 'ramda';
import {
  ApiPerson,
  ApiPersonCrewCredit,
  CanonicalId,
  Id,
  ItemExternalId,
  ItemImage,
  Slug,
} from '.';
import { getTmdbProfileImage } from '../../utils/image-helper';
import { Item, ItemFactory } from './Item';
import PagedResponse from './PagedResponse';

export interface Person {
  adult?: boolean;
  biography?: string;
  birthday?: string;
  cast_credits?: PagedResponse<PersonCastCredit>;
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

export class PersonFactory {
  static create(apiPerson: ApiPerson): Person {
    return {
      ...apiPerson,
      cast_credits: apiPerson.cast_credits
        ? {
            data: apiPerson.cast_credits.data.map(credit => {
              return {
                ...credit,
                item: credit.item ? ItemFactory.create(credit.item) : undefined,
              };
            }),
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
