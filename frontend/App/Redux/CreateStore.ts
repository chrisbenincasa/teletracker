import { createStore, applyMiddleware, compose, StoreCreator, Store, Reducer, Middleware, GenericStoreEnhancer } from 'redux'
import Rehydration from '../Services/Rehydration'
import ReduxPersist from '../Config/ReduxPersist'
import Config from '../Config/DebugConfig'
import createSagaMiddleware, { Task, SagaMiddleware } from 'redux-saga'
// import ScreenTracking from './ScreenTrackingMiddleware'
import { State } from './State';
import { AllEffect } from 'redux-saga/effects';

// creates the store
export default (rootReducer: Reducer<State>, rootSaga: () => IterableIterator<AllEffect>): TeletrackerStore => {  
  /* ------------- Redux Configuration ------------- */

  const middleware: Middleware[] = [];
  const enhancers: GenericStoreEnhancer[] = [];

  /* ------------- Analytics Middleware ------------- */
  // middleware.push(ScreenTracking)

  /* ------------- Saga Middleware ------------- */

  const sagaMonitor = Config.useReactotron ? console.tron.createSagaMonitor() : null;
  const sagaMiddleware = createSagaMiddleware({ sagaMonitor });
  middleware.push(sagaMiddleware);

  /* ------------- Assemble Middleware ------------- */

  enhancers.push(applyMiddleware(...middleware));

  // if Reactotron is enabled (default for __DEV__), we'll create the store through Reactotron
  const createAppropriateStore: StoreCreator = Config.useReactotron ? console.tron.createStore : createStore;
  const store: Store<{}> = createAppropriateStore(rootReducer, compose(...enhancers));

  // kick off root saga
  let sagasManager = sagaMiddleware.run(rootSaga);

  return {
    store,
    sagasManager,
    sagaMiddleware
  }
}

export interface TeletrackerStore {
  store: Store<{}>,
  sagasManager: Task,
  sagaMiddleware: SagaMiddleware<{}>
}
