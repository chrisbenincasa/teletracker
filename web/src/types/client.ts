import {
  ItemType,
  NetworkType,
  OfferType,
  OpenRange,
  SortOptions,
} from './index';
import { SelectableNetworks } from '../utils/searchFilters';

export interface ItemSearchRequest {
  readonly searchText?: string;
  readonly itemTypes?: ItemType[];
  readonly networks?: SelectableNetworks;
  readonly bookmark?: string;
  readonly sort?: SortOptions | 'search_score';
  readonly limit?: number;
  readonly genres?: number[];
  readonly releaseYearRange?: OpenRange;
  readonly castIncludes?: string[];
  readonly crewIncludes?: string[];
  readonly imdbRating?: OpenRange;
  readonly offerTypes?: OfferType[];
}
