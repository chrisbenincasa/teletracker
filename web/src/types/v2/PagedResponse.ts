import { Paging } from '../index';

export default interface PagedResponse<T> {
  readonly data: T[];
  readonly paging?: Paging;
}
