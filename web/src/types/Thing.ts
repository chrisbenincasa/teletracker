import * as R from 'ramda';
import {
  getBackdropPath,
  getDescription,
  getMetadataPath,
  getPosterPath,
  getProfilePath,
} from '../utils/metadata-access';
import { ObjectMetadata } from './external/themoviedb/Movie';
import HasImagery from './HasImagery';
import {
  ActionType,
  Availability,
  CastMember,
  ThingUserMetadata,
} from './index';

export const itemHasTag = (thing: ThingLikeStruct, expectedTag: ActionType) => {
  if (thing.userMetadata) {
    return R.any(tag => {
      return tag.action == expectedTag;
    }, thing.userMetadata.tags);
  }

  return false;
};

export interface ThingLikeStruct {
  id: string;
  name: string;
  normalizedName: string;
  type: 'movie' | 'show' | 'person';
  userMetadata?: ThingUserMetadata;
}

export interface HasThingMetadata {
  metadata?: ObjectMetadata;
}

export interface HasAvailability {
  availability: Availability[];
}

export interface HasCast {
  cast?: CastMember[];
}

export interface HasDescription {
  description?: string;
}

export interface Linkable {
  relativeUrl: string;
}

export interface HasRecommendations<
  T extends ThingLikeStruct &
    HasAvailability &
    HasCast &
    HasThingMetadata = ThingLikeStruct &
    HasAvailability &
    HasCast &
    HasThingMetadata
> {
  recommendations?: T[];
}

export interface ApiThing
  extends ThingLikeStruct,
    HasThingMetadata,
    HasAvailability,
    HasCast,
    HasRecommendations<ApiThing> {}

export default interface Thing
  extends ThingLikeStruct,
    HasImagery,
    HasThingMetadata,
    HasAvailability,
    HasCast,
    HasDescription,
    Linkable,
    HasRecommendations<Thing> {
  // Calculated
  slug: string;
  itemMarkedAsWatched: boolean;
  runtime?: number;
  recommendations?: Thing[];
  genreIds?: number[];
  popularity?: number;
  releaseDate?: number;
}

export class ThingFactory {
  static create(
    thingLike: ThingLikeStruct &
      HasAvailability &
      HasCast &
      HasThingMetadata &
      HasRecommendations,
  ): Thing {
    return {
      ...thingLike,

      // Calculated fields
      slug: thingLike.normalizedName,
      relativeUrl: `/${thingLike.type}/${thingLike.normalizedName}`,
      description: getDescription(thingLike),
      itemMarkedAsWatched: itemHasTag(thingLike, ActionType.Watched),
      runtime:
        thingLike.type === 'movie'
          ? getMetadataPath<number>(thingLike, 'runtime')
          : getMetadataPath<number>(thingLike, 'episode_run_time'),

      // Images
      posterPath: getPosterPath(thingLike),
      backdropPath: getBackdropPath(thingLike),
      profilePath: getProfilePath(thingLike),
      recommendations: (thingLike.recommendations || []).map(
        ThingFactory.create,
      ),
    };
  }

  // static createV2(
  //   item: Item
  // ): Thing {

  // }

  static merge(left: Thing, right: Thing): Thing {
    return R.mergeDeepRight(left, right) as Thing;
  }
}
