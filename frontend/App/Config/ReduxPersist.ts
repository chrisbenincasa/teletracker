// import immutablePersistenceTransform from '../Services/ImmutablePersistenceTransform'
import { AsyncStorage } from 'react-native'
import immutableTransform from 'redux-persist-transform-immutable'
import * as rp from 'redux-persist';
import ImmutablePersistenceTransform from '../Services/ImmutablePersistenceTransform';

// // More info here:  https://shift.infinite.red/shipping-persistant-reducers-7341691232b1
// const REDUX_PERSIST = {
//   active: true,
//   reducerVersion: '1.0',
//   storeConfig: {
//     key: 'primary',
//     storage: AsyncStorage,
//     // Reducer keys that you do NOT want stored to persistence here.
//     blacklist: ['login', 'search', 'nav'],
//     // Optionally, just specify the keys you DO want stored to persistence.
//     // An empty array means 'don't store any reducers' -> infinitered/ignite#409
//     // whitelist: [],
//     transforms: [immutablePersistenceTransform]
//   }
// }

const persistanceLogger = rp.createTransform(
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
