import { ItemType, NetworkType, OpenRange, SortOptions } from './index';

export interface ItemSearchRequest {
  searchText?: string;
  itemTypes?: ItemType[];
  networks?: NetworkType[];
  bookmark?: string;
  sort?: SortOptions | 'search_score';
  limit?: number;
  genres?: number[];
  releaseYearRange?: OpenRange;
  castIncludes?: string[];
  imdbRating?: OpenRange;
}
