import { ItemType, Network, NetworkType, StoredNetworkType } from '../types';
import * as networks from '../constants/networks';
import {
  AmazonVideo,
  AppleTv,
  DisneyPlus,
  Hbo,
  HboMax,
  Hulu,
  Netflix,
  PrimeVideo,
} from '../constants/networks';
import { Item } from '../types/v2/Item';
import _ from 'lodash';
import { ItemExternalId } from '../types/v2';

export enum Platform {
  web = 'web',
}

export const networksToExclude: StoredNetworkType[] = [];

export function storedNetworkTypeToNetworkType(
  storedNetworkType: StoredNetworkType,
): NetworkType {
  if (storedNetworkType === 'hbo') {
    return Hbo;
  } else {
    // All other types match.
    return storedNetworkType as NetworkType;
  }
}

export const sanitizeNetwork = (network: Network) => {
  switch (network.slug) {
    case 'hbo':
      return { ...network, name: 'HBO' };
    default:
      return network;
  }
};

function extractExternalId(
  item: Item,
  networkType: StoredNetworkType | NetworkType,
): ItemExternalId | undefined {
  return _.find(item.external_ids || [], {
    provider: networkType,
  });
}

export function extractExternalIdForDeepLink(
  item: Item,
  networkType: StoredNetworkType,
): ItemExternalId | undefined {
  switch (networkType) {
    // Prime uses the same external IDs as other Amazon items
    case PrimeVideo:
      return extractExternalId(item, networks.AmazonVideo);
    // Temporary mapping from SupportedNetwork['hbo'] to 'hbo-go' that's stored
    // on items
    case 'hbo':
      return extractExternalId(item, networks.Hbo);
    default:
      return extractExternalId(item, networkType);
  }
}

export function deepLinkForId(
  id: string,
  itemType: ItemType,
  networkType: StoredNetworkType | NetworkType,
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
  networkType: StoredNetworkType | NetworkType,
): string | undefined {
  // TODO: We should route these thru a server so we can know when people clicked
  // Or maybe GA
  let typePart;
  // TODO: Produce these URLs as part of the API response.
  switch (networkType) {
    case Netflix:
      return `https://netflix.com/watch/${id}`;
    case Hulu:
      typePart = itemType === 'movie' ? 'movie' : 'series';
      return `https://www.hulu.com/${typePart}/${id}`;
    case 'hbo':
    case Hbo:
      typePart = itemType === 'movie' ? 'feature' : 'series';
      return `https://play.hbogo.com/${typePart}/${id}`;
    case HboMax:
      typePart = itemType === 'movie' ? 'feature' : 'series';
      return `https://play.hbomax.com/${typePart}/${id}`;
    case AmazonVideo:
    case PrimeVideo:
      const baseUrl = `https://www.amazon.com/dp/${id}`;
      if (process.env.REACT_APP_AMAZON_AFFILIATE_ID) {
        return `${baseUrl}?tag=${process.env.REACT_APP_AMAZON_AFFILIATE_ID}`;
      } else {
        return baseUrl;
      }
    case AppleTv:
      typePart = itemType === 'movie' ? 'movie' : 'show';
      return `https://itunes.apple.com/us/${typePart}/${id}`;
    case DisneyPlus:
      typePart = itemType === 'movie' ? 'movies' : 'series';
      const lastSepPos = id.lastIndexOf('_');
      const path = id.substr(0, lastSepPos) + '/' + id.substr(lastSepPos + 1);
      return `https://www.disneyplus.com/${typePart}/${path}`;
    default:
      return;
  }
}
