import * as R from 'ramda';
import {
  ApiItem,
  ItemAvailability,
  ItemCrewMember,
  ItemExternalId,
  ItemGenre,
  ItemImage,
  ItemRating,
  ItemReleaseDate,
  Slug,
  Id,
  Video,
} from '.';
import { ActionType, ItemType } from '..';
import {
  getTmdbBackdropImage,
  getTmdbPosterImage,
  getTmdbProfileImage,
} from '../../utils/image-helper';
import { PersonFactory } from './Person';
import { Person } from './Person';
import _ from 'lodash';

export interface HasSlug {
  slug?: Slug;
}

export interface Item extends HasSlug {
  readonly adult?: boolean;
  readonly availability?: ItemAvailability[];
  readonly cast?: ItemCastMember[];
  readonly crew?: ItemCrewMember[];
  readonly external_ids?: ItemExternalId[];
  readonly genres?: ItemGenre[];
  readonly id: Id;
  readonly images?: ItemImage[];
  readonly original_title: string;
  readonly overview?: string;
  readonly popularity?: number;
  readonly ratings?: ItemRating[];
  readonly recommendations?: Id[];
  readonly release_date?: string;
  readonly release_dates?: ItemReleaseDate[];
  readonly runtime?: number;
  readonly slug: Slug;
  readonly tags?: ItemTag[];
  readonly title?: string;
  readonly type: ItemType;
  readonly videos?: Video[];

  // computed fields
  readonly canonicalId: Id | Slug;
  readonly canonicalTitle: string;
  readonly relativeUrl: string;
  readonly canonicalUrl: string; // Canonical URL used by next.js
  readonly itemMarkedAsWatched: boolean;
  readonly posterImage?: ItemImage;
  readonly backdropImage?: ItemImage;
  readonly profileImage?: string;
}

export interface ItemCastMember {
  readonly character?: string;
  readonly id: string;
  readonly order: number;
  readonly name: string;
  readonly slug: string;
  readonly person?: Person;
}

export interface ItemTag {
  readonly tag: string;
  readonly value?: number;
  readonly string_value?: string;
  readonly userId?: string;
}

export const itemHasTag = (item: ApiItem | Item, expectedTag: ActionType) => {
  if (item.tags) {
    return _.some(item.tags || [], { tag: expectedTag });
  }

  return false;
};

export const getItemTagNumberValue = (
  item: Item,
  tag: string,
): number | undefined => {
  return _.find(item.tags || [], { tag })?.value;
};

export const itemBelongsToLists = (item: ApiItem | Item) => {
  return (item.tags || [])
    .filter(tag => tag.tag.indexOf(ActionType.TrackedInList) !== -1)
    .map(tag => tag.string_value!);
};

export class ItemFactory {
  static create(item: ApiItem): Item {
    const CANONICAL_ID = item.slug || item.id;

    const canonicalTitle =
      item.title && item.title.length
        ? item.title
        : item.alternate_titles && item.alternate_titles.length
        ? item.alternate_titles[0]
        : item.original_title;

    return {
      ...item,
      cast: (item.cast || []).map(castMember => {
        return {
          ...castMember,
          person: castMember.person
            ? PersonFactory.create(castMember.person)
            : undefined,
        } as ItemCastMember;
      }),
      // Calculated fields
      canonicalId: CANONICAL_ID,
      // This will have to change if we ever expand to more regions
      canonicalTitle,
      slug: item.slug,
      relativeUrl: `/${item.type}s/${CANONICAL_ID}`,
      canonicalUrl: `/${item.type}s/[id]?id=${CANONICAL_ID}`,
      // description: getDescription(item),
      itemMarkedAsWatched: itemHasTag(item, ActionType.Watched),

      // Images
      posterImage: getTmdbPosterImage(item),
      backdropImage: getTmdbBackdropImage(item),
      profileImage: getTmdbProfileImage(item),
      recommendations: (item.recommendations || []).map(rec => rec.id),
    };
  }

  static merge(left: Item, right: Item): Item {
    const merger = (key: string, l: any, r: any): any => {
      if (key === 'cast') {
        if (!l) {
          return r;
        } else if (!r) {
          return l;
        } else {
          const leftCast: ItemCastMember[] = l;
          const rightCast: ItemCastMember[] = r;

          const leftCastById = R.mapObjIndexed<
            ItemCastMember[],
            ItemCastMember
          >(R.head, R.groupBy(R.prop('id'), leftCast));

          return R.map(castMember => {
            const leftExisting = leftCastById[castMember.id]
              ? leftCastById[castMember.id]
              : undefined;

            let mergedPerson: Person | undefined;
            if (castMember.person && leftExisting && leftExisting.person) {
              mergedPerson = PersonFactory.merge(
                castMember.person,
                leftExisting.person,
              );
            } else if (castMember.person) {
              mergedPerson = castMember.person;
            } else if (leftExisting) {
              mergedPerson = leftExisting.person;
            }

            return {
              ...castMember,
              person: mergedPerson,
            };
          }, rightCast);
        }
      } else {
        if (!r) {
          return l;
        } else {
          return r;
        }
      }
    };

    return R.mergeDeepWithKey(merger, left, right) as Item;
  }
}
