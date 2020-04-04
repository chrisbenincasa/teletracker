export const wait: (ms: number) => Promise<void> = (ms: number) => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

export async function sequentialPromises<T, U>(
  seq: T[],
  ms: number | undefined,
  itemFn: (item: T) => Promise<U>,
): Promise<U[]> {
  let all = await seq.reduce(async (prev, item) => {
    let last = await prev;

    let result = await itemFn(item);

    if (ms) {
      await wait(ms);
    }

    return [...last, result];
  }, Promise.resolve([] as U[]));

  return Promise.all(all);
}
