import { ItemType, NetworkType } from '../types';
import { HboGo, HboMax, Hulu, Netflix } from '../constants/networks';

export enum Platform {
  web = 'web',
}

export function deepLinkForId(
  id: string,
  itemType: ItemType,
  networkType: NetworkType,
  platform: Platform,
): string | undefined {
  switch (platform) {
    case Platform.web:
      return webDeepLinkForId(id, itemType, networkType);
    default:
      console.error(`Platform ${platform} does not support deep-linking yet.`);
  }
}

function webDeepLinkForId(
  id: string,
  itemType: ItemType,
  networkType: NetworkType,
): string | undefined {
  switch (networkType) {
    case Netflix:
      return `https://netflix.com/watch/${id}`;
    case Hulu:
      const typePart = itemType === 'movie' ? 'movie' : 'series';
      return `https://www.hulu.com/${typePart}/${id}`;
    case HboGo:
      return `https://play.hbogo.com/feature/${id}`;
    case HboMax:
      return `https://play.hbomax.com/feature/${id}`;
    default:
      return;
  }
}
