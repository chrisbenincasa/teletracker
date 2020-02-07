import { routerMiddleware } from 'connected-react-router';
import { createBrowserHistory, createMemoryHistory } from 'history';
import { applyMiddleware, compose, createStore } from 'redux';
import * as rp from 'redux-persist';
import { persistReducer, persistStore, createTransform } from 'redux-persist';
import { createWhitelistFilter } from 'redux-persist-transform-filter';
import createSagaMiddleware from 'redux-saga';
import thunk from 'redux-thunk';
import { root } from './actions';
import createRootReducer from './reducers/';
import {
  State as PeopleState,
  initialState as defaultPeopleState,
} from './reducers/people';

const isClient = typeof window !== 'undefined';

export const history = isClient
  ? createBrowserHistory()
  : createMemoryHistory();

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

// Persis just nameByIdOrSlug on the people state. When rehydrating, initialize everything else to empty
const personNameCache = createTransform<PeopleState, Partial<PeopleState>>(
  (inbound, key) => {
    return {
      nameByIdOrSlug: inbound.nameByIdOrSlug,
    };
  },
  (outbound, key) => {
    return {
      ...outbound,
      ...defaultPeopleState,
    };
  },
  { whitelist: ['people'] },
);

const getWhitelists = () => {
  return ['auth', 'people'];
};

function getPersistConfig() {
  const localforage = require('localforage');

  return {
    key: 'root',
    storage: localforage,
    whitelist: getWhitelists(),
    transforms: [authWhitelistFilter, personNameCache],
  };
}

if (env === 'development' && typeof window !== 'undefined') {
  const devToolsExtension = (window as any).__REDUX_DEVTOOLS_EXTENSION__;

  if (typeof devToolsExtension === 'function') {
    enhancers.push(devToolsExtension({ trace: true }));
  }
}

export default () => {
  let store, persistor;
  const reducerWithHistory = createRootReducer(history);

  if (isClient) {
    const composedEnhancers = compose(
      applyMiddleware(...middleware),
      ...enhancers,
    );

    const persistedReducer = persistReducer(
      getPersistConfig(),
      reducerWithHistory,
    );
    store = createStore(persistedReducer, initialState, composedEnhancers);
  } else {
    store = createStore(
      reducerWithHistory,
      initialState,
      applyMiddleware(sagaMiddleware),
    );
  }

  sagaMiddleware.run(root);

  if (isClient) {
    persistor = persistStore(store);
  }

  return { store, persistor };
};
