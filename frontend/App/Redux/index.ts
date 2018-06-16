import { combineReducers, Store } from 'redux';
import { persistReducer } from 'redux-persist';

import ReduxPersist from '../Config/ReduxPersist';
import rootSaga from '../Sagas/';
import configureStore from './CreateStore';
import { State } from './State';
import Rehydration from '../Services/Rehydration';
import { reducer as UserReducer } from './UserRedux';

export const reducers = combineReducers<State>({
  user: UserReducer
})

export default (): { store: Store<{}>, persistor: any } => {
  let finalReducers = reducers;

  // If rehydration is on use persistReducer otherwise default combineReducers
  if (ReduxPersist.active) {
    finalReducers = ReduxPersist.createPersistor(finalReducers)
  }

  let { store, sagasManager, sagaMiddleware } = configureStore(finalReducers, rootSaga);

  let persistor = ReduxPersist.persistStore(store);

  // configure persistStore and check reducer version number
  if (ReduxPersist.active) {
    Rehydration.updateReducers(store, persistor)
  }

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

  return { store, persistor };
}
