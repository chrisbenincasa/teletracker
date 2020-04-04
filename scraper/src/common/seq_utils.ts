import _ from 'lodash';
import { wait } from './promise_utils';

declare global {
  interface Array<T> {
    filterMod(mod: number | undefined, band: number | undefined): Array<T>;
    page(offset: number, limit: number): Array<T>;

    // async
    sequentialPromises<U>(
      fn: (item: T) => Promise<U>,
      ms?: number,
    ): Promise<Array<U>>;
  }
}

export function filterMod<T>(
  arr: T[],
  mod: number | undefined,
  band: number | undefined,
) {
  return arr.filter((item, idx) => {
    if (!_.isUndefined(mod) && !_.isUndefined(band)) {
      return idx % mod === band;
    } else {
      return true;
    }
  });
}

Array.prototype.filterMod = function (
  mod: number | undefined,
  band: number | undefined,
) {
  return filterMod(this, mod, band);
};

Array.prototype.page = function (offset: number, limit: number) {
  return this.slice(offset, limit < 0 ? this.length : offset + limit);
};

Array.prototype.sequentialPromises = async function <T, U>(
  fn: (item: T) => Promise<U>,
  ms?: number,
): Promise<U[]> {
  let all = await this.reduce(async (prev, item) => {
    let last = await prev;

    let result = await fn(item);

    if (ms) {
      await wait(ms);
    }

    return [...last, result];
  }, Promise.resolve([]));

  return Promise.all(all);
};
