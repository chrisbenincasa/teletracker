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

export interface HasSlug {
  slug?: Slug;
}

export interface Item extends HasSlug {
  adult?: boolean;
  availability?: ItemAvailability[];
  cast?: ItemCastMember[];
  crew?: ItemCrewMember[];
  external_ids?: ItemExternalId[];
  genres?: ItemGenre[];
  id: string;
  images?: ItemImage[];
  original_title: string;
  overview?: string;
  popularity?: number;
  ratings?: ItemRating[];
  recommendations?: Item[];
  release_date?: string;
  release_dates?: ItemReleaseDate[];
  runtime?: number;
  slug: Slug;
  tags?: ItemTag[];
  title?: string;
  type: ItemType;
  videos?: Video[];

  // computed fields
  canonicalId: Id | Slug;
  canonicalTitle: string;
  relativeUrl: string;
  canonicalUrl: string; // Canonical URL used by next.js
  itemMarkedAsWatched: boolean;
  posterImage?: ItemImage;
  backdropImage?: ItemImage;
  profileImage?: string;
}

export interface ItemCastMember {
  character?: string;
  id: string;
  order: number;
  name: string;
  slug: string;
  person?: Person;
}

export interface ItemTag {
  tag: string;
  value?: number;
  string_value?: string;
  userId?: string;
}

export const itemHasTag = (thing: ApiItem | Item, expectedTag: ActionType) => {
  if (thing.tags) {
    return R.any((tag: ItemTag) => {
      return tag.tag === expectedTag;
    }, thing.tags);
  }

  return false;
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
      recommendations: (item.recommendations || []).map(ItemFactory.create),
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
