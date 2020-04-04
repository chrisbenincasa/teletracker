export const timeAsync = async <T>(key: string, fn: () => Promise<T>) => {
  console.time(key);
  let result = await fn();
  console.timeEnd(key);
  return result;
};
