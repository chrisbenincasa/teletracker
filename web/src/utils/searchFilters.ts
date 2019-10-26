import {
  isItemType,
  isListSortOption,
  isNetworkType,
  ItemType,
  ListSortOptions,
  NetworkType,
} from '../types';

export interface FilterParams {
  genresFilter?: number[];
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  sortOrder: ListSortOptions;
}

export function parseFilterParamsFromQs(qs: string): FilterParams {
  let params = new URLSearchParams(qs);
  let sortParam = params.get('sort');
  let itemTypeParam = params.get('type');
  let networkParam = params.get('networks');
  let genresParam = params.get('genres');

  return {
    sortOrder:
      sortParam && isListSortOption(sortParam)
        ? (sortParam as ListSortOptions)
        : 'default',

    itemTypes: itemTypeParam
      ? decodeURIComponent(itemTypeParam)
          .split(',')
          .filter(isItemType)
      : undefined,

    networks: networkParam
      ? decodeURIComponent(networkParam)
          .split(',')
          .filter(isNetworkType)
      : undefined,

    genresFilter: genresParam
      ? decodeURIComponent(genresParam)
          .split(',')
          .map(item => {
            return parseInt(item, 10);
          })
      : undefined,
  };
}
