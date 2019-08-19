import { ConnectedRouter } from 'connected-react-router';
import * as firebase from 'firebase/app';
import 'firebase/auth';
import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import 'sanitize.css/sanitize.css';
import App from './containers/app';
import './index.css';
import createStore, { history } from './store';

const firebaseConfig = {
  apiKey: 'AIzaSyAnt0_Kk2HihdTg8jcqozLTlL1qTad09-k',
  authDomain: 'teletracker.firebaseapp.com',
  databaseURL: 'https://teletracker.firebaseio.com',
  projectId: 'teletracker',
  storageBucket: 'teletracker.appspot.com',
  messagingSenderId: '558300338939',
  appId: '1:558300338939:web:9cc68ec974ab8960',
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);

const target = document.querySelector('#root');

export const { store, persistor } = createStore();

render(
  <Provider store={store}>
    <PersistGate loading={null} persistor={persistor}>
      <ConnectedRouter history={history}>
        <div>
          <App />
        </div>
      </ConnectedRouter>
    </PersistGate>
  </Provider>,
  target,
);
