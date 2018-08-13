import { combineReducers, Store } from 'redux';

import ReduxPersist from '../Config/ReduxPersist';
import rootSaga from '../Sagas/';
import Rehydration from '../Services/Rehydration';
import configureStore from './CreateStore';
import { reducer as EventsReducer } from './EventsRedux';
import { reducer as ItemsReducer } from './ItemRedux';
import { reducer as ListReducer } from './ListRedux';
import { reducer as SearchReducer } from './SearchRedux';
import { State } from './State';
import { reducer as UserReducer } from './UserRedux';

export const reducers = combineReducers<State>({
  user: UserReducer,
  search: SearchReducer,
  lists: ListReducer,
  events: EventsReducer,
  items: ItemsReducer
});

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
