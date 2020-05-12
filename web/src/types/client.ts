import { ItemType, NetworkType, OpenRange, SortOptions } from './index';
import { List } from 'immutable';

export interface ItemSearchRequest {
  searchText?: string;
  itemTypes?: List<ItemType>;
  networks?: List<NetworkType>;
  bookmark?: string;
  sort?: SortOptions | 'search_score';
  limit?: number;
  genres?: List<number>;
  releaseYearRange?: OpenRange;
  castIncludes?: List<string>;
  imdbRating?: OpenRange;
}
