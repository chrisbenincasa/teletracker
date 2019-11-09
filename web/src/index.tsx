import { ConnectedRouter } from 'connected-react-router';
import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import 'sanitize.css/sanitize.css';
import App from './containers/App';
import './index.css';
import createStore, { history } from './store';
import { createMuiTheme, MuiThemeProvider } from '@material-ui/core';
import { blueGrey } from '@material-ui/core/colors';
import Amplify from '@aws-amplify/core';
import { launchUri } from '@aws-amplify/auth/lib/OAuth/urlOpener';

Amplify.configure({
  Auth: {
    region: 'us-west-2',
    userPoolId: 'us-west-2_K6E5m6v90', // TODO: Make variable
    userPoolWebClientId: '3e5t3s3ddfci044230p3f3ki29',
    mandatorySignIn: false,

    oauth: {
      domain: 'auth.qa.teletracker.tv', // TODO config
      scope: ['email', 'openid'],
      redirectSignIn: 'http://localhost:3000/login', // TODO: config,
      redirectSignOut: 'http://localhost:3000', // TODO: config,
      responseType: 'code',
      urlOpener: async (url, redirectUrl) => {
        if (url.includes('logout')) {
          // Unsure if this is worth keeping, but it allows a logout to
          // happen without a page refresh
          let img = document.createElement('img');
          img.src = url;
          document.body.appendChild(img);
          return new Promise((resolve, reject) => {
            img.onload = resolve;
            img.onerror = reject;
          })
            .then(() => {
              window.history.pushState({}, '', redirectUrl);
            })
            .catch(console.error);
        } else {
          return launchUri(url);
        }
      },
    },
  },
});

const target = document.querySelector('#root');

export const { store, persistor } = createStore();

const theme = createMuiTheme({
  palette: {
    primary: blueGrey,
    type: 'dark',
  },
});

render(
  <Provider store={store}>
    <PersistGate loading={null} persistor={persistor}>
      <ConnectedRouter history={history}>
        <MuiThemeProvider theme={theme}>
          <div>
            <App />
          </div>
        </MuiThemeProvider>
      </ConnectedRouter>
    </PersistGate>
  </Provider>,
  target,
);
