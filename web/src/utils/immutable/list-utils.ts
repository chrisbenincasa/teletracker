import Immutable from 'immutable';
import _ from 'lodash';

const collect = <T, U>(
  l: Immutable.List<T>,
  mapper: (item: T) => U | undefined,
): Immutable.List<U> => {
  return l.flatMap(value => {
    const res = mapper(value);
    if (_.isUndefined(value)) {
      return Immutable.List();
    } else {
      return Immutable.List.of(res!);
    }
  });
};

export default {
  collect,
};
