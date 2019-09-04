import { ThingLikeStruct } from './Thing';
import { Person as TmdbPerson } from './external/themoviedb/Person';
import HasImagery from './HasImagery';
import * as R from 'ramda';
import { PersonCredit, PersonCreditFactory } from './PersonCredit';

export interface PersonStruct extends ThingLikeStruct {
  metadata: TmdbPerson;
}

export interface ApiPerson extends PersonStruct {
  type: 'person';
}

export default interface Person extends PersonStruct, HasImagery {
  type: 'person';
  // Calculated
  castMemberOf: PersonCredit[];
  biography?: string;
  genreIds?: number[];
}

export class PersonFactory {
  static create(apiPerson: ApiPerson): Person {
    return {
      ...apiPerson,
      castMemberOf: apiPerson.metadata.combined_credits
        ? (apiPerson.metadata.combined_credits.cast || []).map(
            PersonCreditFactory.create,
          )
        : [],
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
