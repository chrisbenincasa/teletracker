import { connectRouter, routerMiddleware } from 'connected-react-router';
import { createBrowserHistory } from 'history';
import { applyMiddleware, compose, createStore } from 'redux';
import * as rp from 'redux-persist';
import { persistReducer, persistStore } from 'redux-persist';
import storage from 'redux-persist/lib/storage';
import thunk from 'redux-thunk';
import rootReducer from './reducers/';
import createSagaMiddleware from 'redux-saga';
import { root } from './actions';

export const history = createBrowserHistory();

const initialState = {};
const enhancers: any[] = [];
const sagaMiddleware = createSagaMiddleware();
const middleware = [thunk, routerMiddleware(history), sagaMiddleware];

let env = process.env.NODE_ENV;

const getBlacklists = () => {
  // if (env === 'development') {
  //   return [];
  // } else {
  // }
  return ['search'];
};

const persistConfig: rp.PersistConfig = {
  key: 'root',
  storage,
  blacklist: getBlacklists(),
};

if (env === 'development') {
  const devToolsExtension = (window as any).__REDUX_DEVTOOLS_EXTENSION__;

  if (typeof devToolsExtension === 'function') {
    enhancers.push(devToolsExtension());
  }
}

const composedEnhancers = compose(
  applyMiddleware(...middleware),
  ...enhancers,
);

const reducerWithHistory = connectRouter(history)(rootReducer);

const persistedReducer = persistReducer(persistConfig, reducerWithHistory);

export default () => {
  let store = createStore(persistedReducer, initialState, composedEnhancers);
  sagaMiddleware.run(root);
  let persistor = persistStore(store);
  return { store, persistor };
};
