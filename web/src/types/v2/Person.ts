import * as R from 'ramda';
import {
  ApiPerson,
  ItemExternalId,
  ItemImage,
  ApiPersonCastCredit,
  ApiPersonCrewCredit,
} from '.';
import { getTmdbProfileImage } from '../../utils/image-helper';
import { Item, ItemFactory } from './Item';

export interface Person {
  adult?: boolean;
  biography?: string;
  birthday?: string;
  cast_credits?: PersonCastCredit[];
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

  // Compute fields
  profile_path?: string;
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
      cast_credits: (apiPerson.cast_credits || []).map(credit => {
        return {
          ...credit,
          item: credit.item ? ItemFactory.create(credit.item) : undefined,
        };
      }),
      profile_path: getTmdbProfileImage(apiPerson as ApiPerson),
      backdrop_path: undefined,
      poster_path: undefined,
    } as Person;
  }

  static merge(left: Person, right: Person) {
    return R.mergeDeepRight(left, right) as Person;
  }
}
