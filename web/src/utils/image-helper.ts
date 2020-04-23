import { ApiItem, ItemImage } from '../types/v2';
import _ from 'lodash';
import { ApiPerson, Person } from '../types/v2/Person';
import { Item } from '../types/v2/Item';

export function getTmdbPosterImage(
  item: ApiItem | Item,
): ItemImage | undefined {
  return getTmdbImage(item, 'poster');
}

export function getTmdbBackdropImage(
  item: ApiItem | Item,
): ItemImage | undefined {
  return getTmdbImage(item, 'backdrop');
}

export function getTmdbProfileImage(
  item: ApiItem | Item | ApiPerson | Person,
): string | undefined {
  let img = getTmdbImage(item, 'profile');
  return img ? img.id : undefined;
}

export function getTmdbImage(
  item: ApiItem | Item | ApiPerson | Person,
  imageType: string,
): ItemImage | undefined {
  return _.find(
    item.images || [],
    image => image.provider_id === 0 && image.image_type === imageType,
  );
}
