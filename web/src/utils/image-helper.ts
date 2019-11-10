import { ApiItem, ItemImage, ApiPerson } from '../types/v2';
import _ from 'lodash';

export function getTmdbPosterImage(item: ApiItem): ItemImage | undefined {
  return getTmdbImage(item, 'poster');
}

export function getTmdbBackdropImage(item: ApiItem): ItemImage | undefined {
  return getTmdbImage(item, 'backdrop');
}

export function getTmdbProfileImage(
  item: ApiItem | ApiPerson,
): string | undefined {
  let img = getTmdbImage(item, 'profile');
  return img ? img.id : undefined;
}

export function getTmdbImage(
  item: ApiItem | ApiPerson,
  imageType: string,
): ItemImage | undefined {
  return _.find(
    item.images || [],
    image => image.provider_id === 0 && image.image_type === imageType,
  );
}
