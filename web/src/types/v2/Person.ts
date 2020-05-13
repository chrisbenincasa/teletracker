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
  readonly adult?: boolean;
  readonly biography?: string;
  readonly birthday?: string;
  readonly cast_credits?: PagedResponse<PersonCastCredit>;
  readonly cast_credit_ids?: PagedResponse<string>;
  readonly crew_credits?: ApiPersonCrewCredit[];
  readonly external_ids?: string[];
  readonly deathday?: string;
  readonly homepage?: string;
  readonly id: Id;
  readonly images?: ItemImage[];
  readonly name: string;
  readonly place_of_birth?: string;
  readonly popularity?: number;
  readonly slug?: Slug;

  // Compute fields
  readonly profile_path?: string;
  readonly canonical_id: CanonicalId;
  readonly canonicalUrl: string;
  readonly relativeUrl: string;
}

export interface PersonCastCredit {
  readonly character?: string;
  readonly id: string;
  readonly title: string;
  readonly type: string;
  readonly slug: string;
  readonly item?: Item;
}

export interface ApiPersonCastCredit {
  readonly character?: string;
  readonly id: string;
  readonly title: string;
  readonly type: string;
  readonly slug: string;
  readonly item?: ApiItem;
}

export interface ApiPerson {
  readonly adult?: boolean;
  readonly biography?: string;
  readonly birthday?: string;
  readonly cast_credits?: PagedResponse<ApiPersonCastCredit>;
  readonly crew_credits?: ApiPersonCrewCredit[];
  readonly external_ids?: string[];
  readonly deathday?: string;
  readonly homepage?: string;
  readonly id: Id;
  readonly images?: ItemImage[];
  readonly name: string;
  readonly place_of_birth?: string;
  readonly popularity?: number;
  readonly slug?: Slug;
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
