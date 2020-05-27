import { ApiItem, ItemImage } from '../types/v2';
import _ from 'lodash';
import { ApiPerson, Person } from '../types/v2/Person';
import { Item } from '../types/v2/Item';
import { NetworkType } from '../types';

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

// !exactMatch lets you bypass the 'hbo-now' logic so we can show both 'hbo-go' and 'hbo-now' on availability page but just 'hbo' on other screens
export function getLogoUrl(network: NetworkType, exactMatch?: boolean) {
  if (network === 'hbo-now' && !exactMatch) {
    return `/images/logos/hbo/hbo-full.svg`;
  } else {
    return `/images/logos/${network}/${network}-full.svg`;
  }
}
