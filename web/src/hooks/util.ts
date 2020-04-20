import deepEq from 'dequal';

export function hookDeepEqual<T>(left: T | undefined, right: T | undefined) {
  if (left && !right) {
    return false;
  } else if (!left && right) {
    return false;
  } else if (!left && !right) {
    return true;
  }

  return deepEq(left, right);
}
