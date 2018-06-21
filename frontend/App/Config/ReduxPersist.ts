// import immutablePersistenceTransform from '../Services/ImmutablePersistenceTransform'
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

const defaultPersistConfig: rp.PersistConfig = {
  key: 'root',
  storage: AsyncStorage,
  debug: true,
  blacklist: ['search'],
  transforms: [ImmutablePersistenceTransform, persistanceLogger]
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
