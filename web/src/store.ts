import { applyMiddleware, compose, createStore } from 'redux';
import createSagaMiddleware from 'redux-saga';
import { root } from './actions';
import createRootReducer from './reducers/';
import { configureStore, getDefaultMiddleware } from '@reduxjs/toolkit';

const isClient = typeof window !== 'undefined';

const enhancers: any[] = [];

let env = process.env.NODE_ENV;

// Connect devtools if we're in dev mode
if (env === 'development' && isClient) {
  const devToolsExtension = (window as any).__REDUX_DEVTOOLS_EXTENSION__;

  if (typeof devToolsExtension === 'function') {
    enhancers.push(devToolsExtension({ trace: true }));
  }
}

export function makeStore(initialState) {
  const sagaMiddleware = createSagaMiddleware();

  const store = configureStore({
    reducer: createRootReducer(),
    middleware: [...getDefaultMiddleware(), sagaMiddleware],
    preloadedState: initialState,
  });

  sagaMiddleware.run(root);

  return store;
}

export default initialState => {
  const reducerWithHistory = createRootReducer();
  const sagaMiddleware = createSagaMiddleware();
  const composedEnhancers = compose(
    applyMiddleware(sagaMiddleware),
    ...enhancers,
  );

  const store = createStore(
    reducerWithHistory,
    initialState,
    composedEnhancers,
  );

  sagaMiddleware.run(root);

  return { store };
};
