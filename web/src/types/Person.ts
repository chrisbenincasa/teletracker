import { HasDescription, Linkable, ThingLikeStruct } from './Thing';
import { Person as TmdbPerson } from './external/themoviedb/Person';
import HasImagery from './HasImagery';
import * as R from 'ramda';
import { PersonCredit, PersonCreditFactory } from './PersonCredit';

export interface PersonStruct extends ThingLikeStruct {
  metadata: TmdbPerson;
}

export interface ApiPerson extends PersonStruct {
  type: 'person';
  credits?: ApiPersonCredit[];
}

export interface ApiPersonCredit
  extends HasImagery,
    ThingLikeStruct,
    HasDescription,
    Linkable {
  id: string;
  name: string;
  normalizedName: string;
  tmdbId?: string;
  popularity?: number;
  type: 'movie' | 'show';
  associationType: string;
  characterName?: string;
}

export default interface Person extends PersonStruct, HasImagery {
  type: 'person';
  // Calculated
  credits?: PersonCredit[];
  biography?: string;
  genreIds?: number[];
}

export class PersonFactory {
  static create(apiPerson: ApiPerson): Person {
    return {
      ...apiPerson,
      credits: (apiPerson.credits || []).map(PersonCreditFactory.create),
      biography: apiPerson.metadata.biography,
      profilePath: apiPerson.metadata.profile_path,
      backdropPath: undefined,
      posterPath: undefined,
    };
  }

  static merge(left: Person, right: Person) {
    return R.mergeDeepRight(left, right) as Person;
  }
}
