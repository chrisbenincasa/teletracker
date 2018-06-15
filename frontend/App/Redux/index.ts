import { combineReducers, Store } from 'redux';
import { persistReducer } from 'redux-persist';

import ReduxPersist from '../Config/ReduxPersist';
import rootSaga from '../Sagas/';
import configureStore from './CreateStore';
import { State } from './State';
import { reducer as UserReducer } from './UserRedux';

export const reducers = combineReducers<State>({
  user: UserReducer
})

export default (): Store<{}> => {
  let finalReducers = reducers
  // If rehydration is on use persistReducer otherwise default combineReducers
  if (ReduxPersist.active) {
    const persistConfig = ReduxPersist.storeConfig
    finalReducers = persistReducer(persistConfig, reducers)
  }

  let { store, sagasManager, sagaMiddleware } = configureStore(finalReducers, rootSaga)

  if (module.hot) {
    module.hot.accept(() => {
      const nextRootReducer = require('./').reducers
      store.replaceReducer(nextRootReducer)

      const newYieldedSagas = require('../Sagas').default
      sagasManager.cancel()
      sagasManager.done.then(() => {
        sagasManager = sagaMiddleware.run(newYieldedSagas)
      })
    })
  }

  return store
}
