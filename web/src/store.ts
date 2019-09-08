import { connectRouter, routerMiddleware } from 'connected-react-router';
import { createBrowserHistory } from 'history';
import * as localforage from 'localforage';
import { applyMiddleware, compose, createStore } from 'redux';
import * as rp from 'redux-persist';
import { persistReducer, persistStore } from 'redux-persist';
import {
  createBlacklistFilter,
  createWhitelistFilter,
} from 'redux-persist-transform-filter';
import createSagaMiddleware from 'redux-saga';
import thunk from 'redux-thunk';
import { root } from './actions';
import rootReducer from './reducers/';

export const history = createBrowserHistory();

const initialState = {};
const enhancers: any[] = [];
const sagaMiddleware = createSagaMiddleware();
const middleware = [thunk, routerMiddleware(history), sagaMiddleware];

let env = process.env.NODE_ENV;

const authWhitelistFilter = createWhitelistFilter(
  'auth',
  ['token', 'user'],
  ['token', 'user'],
);

const getWhitelists = () => {
  // if (env === 'development') {
  //   return [];
  // } else {
  // }
  return ['auth'];
};

export const persistConfig: rp.PersistConfig = {
  key: 'root',
  storage: localforage,
  whitelist: getWhitelists(),
  // blacklist: getBlacklists(),
  transforms: [authWhitelistFilter],
};

if (env === 'development') {
  const devToolsExtension = (window as any).__REDUX_DEVTOOLS_EXTENSION__;

  if (typeof devToolsExtension === 'function') {
    enhancers.push(devToolsExtension({ trace: true }));
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
