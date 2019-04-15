import { createStore, applyMiddleware, compose } from 'redux'
import { connectRouter, routerMiddleware } from 'connected-react-router'
import { persistStore, persistReducer } from "redux-persist";
import storage from 'redux-persist/lib/storage';
import thunk from 'redux-thunk'
import createHistory from 'history/createBrowserHistory'
import rootReducer from './reducers/'

export const history = createHistory()

const initialState = {}
const enhancers: any[] = []
const middleware = [thunk, routerMiddleware(history)]

const persistConfig = {
  key: "root",
  storage
};

if (process.env.NODE_ENV === 'development') {
  const devToolsExtension = (window as any).__REDUX_DEVTOOLS_EXTENSION__

  if (typeof devToolsExtension === 'function') {
    enhancers.push(devToolsExtension())
  }
}

const composedEnhancers = compose(
  applyMiddleware(...middleware),
  ...enhancers
)

const reducerWithHistory = connectRouter(history)(rootReducer);

const persistedReducer = persistReducer(persistConfig, reducerWithHistory);

export default () => {
  let store = createStore(persistedReducer, initialState, composedEnhancers);
  let persistor = persistStore(store);
  return { store, persistor };
}