import HasImagery from './HasImagery';
import { ApiPersonCredit } from './Person';
import { HasDescription, Linkable, ThingLikeStruct } from './Thing';

export interface PersonCredit
  extends HasImagery,
    ThingLikeStruct,
    HasDescription,
    Linkable {
  genreIds?: number[];
  popularity?: number;
  releaseDate?: string;
}

export class PersonCreditFactory {
  static create(credit: ApiPersonCredit): PersonCredit {
    return {
      ...credit,
      relativeUrl: `/${credit.type}/${credit.normalizedName}`,
    };
  }
}
