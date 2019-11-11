import { Paging } from '../index';

export default interface PagedResponse<T> {
  data: T[];
  paging?: Paging;
}
