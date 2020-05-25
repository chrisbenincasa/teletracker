import { ItemType, NetworkType, OpenRange, SortOptions } from './index';

export interface ItemSearchRequest {
  readonly searchText?: string;
  readonly itemTypes?: ItemType[];
  readonly networks?: NetworkType[];
  readonly bookmark?: string;
  readonly sort?: SortOptions | 'search_score';
  readonly limit?: number;
  readonly genres?: number[];
  readonly releaseYearRange?: OpenRange;
  readonly castIncludes?: string[];
  readonly crewIncludes?: string[];
  readonly imdbRating?: OpenRange;
}
