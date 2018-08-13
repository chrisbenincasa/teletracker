import { createFilter, createBlacklistFilter } from 'redux-persist-transform-filter';
import { AsyncStorage } from 'react-native';
import * as rp from 'redux-persist';

import ImmutablePersistenceTransform from '../Services/ImmutablePersistenceTransform';

export const persistanceLogger = rp.createTransform(
  (inboundState, key) => {
    return inboundState
  },
  (outboundState, key) => {
    return outboundState
  }
);

// you want to remove some keys before you save
const saveSubsetBlacklistFilter = createBlacklistFilter(
    'user',
    ['signup']
);

const defaultPersistConfig: rp.PersistConfig = {
  key: 'root',
  storage: AsyncStorage,
  debug: true,
  blacklist: ['search', 'nav'],
  transforms: [ImmutablePersistenceTransform, saveSubsetBlacklistFilter]
}

export function createPersistor(reducers) {
  return rp.persistReducer(defaultPersistConfig, reducers)
}

export function persistStore(store) {
  return rp.persistStore(store);
}

export default {
  active: true,
  createPersistor,
  persistStore
}
