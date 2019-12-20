export const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

export const sequentialPromises = async (seq, ms, itemFn) => {
  console.log('seq', seq);
  let all = await seq.reduce(async (prev, item) => {
    let last = await prev;

    let result = await itemFn(item);

    if (ms) {
      await wait(ms);
    }

    return [...last, result];
  }, Promise.resolve([]));

  return Promise.all(all);
};
