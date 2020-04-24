import { createSelectorCreator, defaultMemoize } from 'reselect';
import { hookDeepEqual } from './util';

const createDeepEqSelector = createSelectorCreator(
  defaultMemoize,
  hookDeepEqual,
);

export default createDeepEqSelector;
