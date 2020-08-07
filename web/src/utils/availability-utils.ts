import { ItemType, NetworkType, Network } from '../types';
import { AmazonVideo, Hulu, Netflix, PrimeVideo } from '../constants/networks';
import { Item } from '../types/v2/Item';
import _ from 'lodash';
import { ItemExternalId } from '../types/v2';
import * as networks from '../constants/networks';

export enum Platform {
  web = 'web',
}

export const networksToExclude: NetworkType[] = ['hbo-now'];

export const sanitizeNetwork = (network: Network) => {
  switch (network.slug) {
    case 'hbo-go':
      return { ...network, name: 'HBO' };
    default:
      return network;
  }
};

function extractExternalId(
  item: Item,
  networkType: NetworkType,
): ItemExternalId | undefined {
  return _.find(item.external_ids || [], {
    provider: networkType,
  });
}

export function extractExternalIdForDeepLink(
  item: Item,
  networkType: NetworkType,
): ItemExternalId | undefined {
  switch (networkType) {
    // Prime uses the same external IDs as other Amazon items
    case 'amazon-prime-video':
      return extractExternalId(item, networks.AmazonVideo);
    default:
      return extractExternalId(item, networkType);
  }
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
  // TODO: We should route these thru a server so we can know when people clicked
  // Or maybe GA
  let typePart;
  switch (networkType) {
    case Netflix:
      return `https://netflix.com/watch/${id}`;
    case Hulu:
      typePart = itemType === 'movie' ? 'movie' : 'series';
      return `https://www.hulu.com/${typePart}/${id}`;
    case 'hbo-go':
      typePart = itemType === 'movie' ? 'feature' : 'series';
      return `https://play.hbogo.com/${typePart}/${id}`;
    case 'hbo-max':
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
    default:
      return;
  }
}
