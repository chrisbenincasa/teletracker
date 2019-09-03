import HasImagery from './HasImagery';
import { PersonCredit as TmdbPersonCredit } from './external/themoviedb/Person';

export interface PersonCredit extends HasImagery {
  genreIds?: number[];
  popularity?: number;
  releaseDate?: string;
}

export class PersonCreditFactory {
  static create(person: TmdbPersonCredit): PersonCredit {
    return {
      ...person,
      profilePath: undefined,
      backdropPath: person.backdrop_path,
      posterPath: person.poster_path,
      genreIds: person.genre_ids,
      popularity: person.popularity,
      releaseDate: person.release_date,
    };
  }
}
