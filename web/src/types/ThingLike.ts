import { ThingLikeStruct } from './Thing';

export default abstract class ThingLike<Underlying extends ThingLikeStruct> {
  protected readonly underlying: Underlying;

  protected constructor(underlying: Underlying) {
    this.underlying = underlying;
  }

  get raw(): Underlying {
    return this.underlying;
  }

  get id(): string {
    return this.underlying.id;
  }

  get name(): string {
    return this.underlying.name;
  }

  get normalizedName(): string {
    return this.raw.normalizedName;
  }

  get slug(): string {
    return this.raw.normalizedName;
  }

  abstract get type(): 'movie' | 'show' | 'person';
}
