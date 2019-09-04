import { ObjectMetadata } from './external/themoviedb/Movie';
import {
  ActionType,
  Availability,
  CastMember,
  ThingUserMetadata,
} from './index';
import * as R from 'ramda';
import {
  getBackdropPath,
  getDescription,
  getMetadataPath,
  getPosterPath,
  getProfilePath,
} from '../utils/metadata-access';
import HasImagery from './HasImagery';

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

export interface ApiThing
  extends ThingLikeStruct,
    HasThingMetadata,
    HasAvailability,
    HasCast {}

export default interface Thing
  extends ThingLikeStruct,
    HasImagery,
    HasThingMetadata,
    HasAvailability,
    HasCast,
    HasDescription,
    Linkable {
  // Calculated
  slug: string;
  itemMarkedAsWatched: boolean;
  runtime?: number;
}

export class ThingFactory {
  static create(
    apiThing: ThingLikeStruct & HasAvailability & HasCast & HasThingMetadata,
  ): Thing {
    return {
      ...apiThing,

      // Calculated fields
      slug: apiThing.normalizedName,
      relativeUrl: `/${apiThing.type}/${apiThing.normalizedName}`,
      description: getDescription(apiThing),
      itemMarkedAsWatched: itemHasTag(apiThing, ActionType.Watched),
      runtime:
        apiThing.type === 'movie'
          ? getMetadataPath<number>(apiThing, 'runtime')
          : getMetadataPath<number>(apiThing, 'episode_run_time'),

      // Images
      posterPath: getPosterPath(apiThing),
      backdropPath: getBackdropPath(apiThing),
      profilePath: getProfilePath(apiThing),
    };
  }

  static merge(left: Thing, right: Thing): Thing {
    return R.mergeDeepRight(left, right) as Thing;
  }
}
