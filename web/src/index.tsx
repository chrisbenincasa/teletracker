import React from 'react';
import {
  createMuiTheme,
  MuiThemeProvider,
  responsiveFontSizes,
  Theme,
} from '@material-ui/core';
import { ConnectedRouter } from 'connected-react-router';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import 'sanitize.css/sanitize.css';
import App from './containers/App';
import './index.css';
import createStore, { history } from './store';
import Amplify from '@aws-amplify/core';
import { launchUri } from '@aws-amplify/auth/lib/OAuth/urlOpener';
import { hexToRGB } from './utils/style-utils';

Amplify.configure({
  Auth: {
    region: process.env.REACT_APP_AUTH_REGION,
    userPoolId: process.env.REACT_APP_USER_POOL_ID,
    userPoolWebClientId: process.env.REACT_APP_USER_POOL_CLIENT_ID,
    mandatorySignIn: false,

    oauth: {
      domain: process.env.REACT_APP_AUTH_DOMAIN,
      scope: ['email', 'openid'],
      redirectSignIn: process.env.REACT_APP_AUTH_REDIRECT_URI,
      redirectSignOut: process.env.REACT_APP_AUTH_REDIRECT_SIGNOUT_URI,
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

declare module '@material-ui/core/styles/createMuiTheme' {
  interface Theme {
    custom: {
      borderRadius: {
        circle: string;
      };
      hover: {
        active: string;
        opacity: number;
      };
      backdrop: {
        backgroundColor: string;
        backgroundImage: string;
      };
    };
  }
  // allow configuration using `createMuiTheme`
  interface ThemeOptions {
    custom?: {
      borderRadius?: {
        circle?: string;
      };
      hover?: {
        active?: string;
        opacity?: number;
      };
      backdrop?: {
        backgroundColor?: string;
        backgroundImage?: string;
      };
    };
  }
}

let theme = createMuiTheme({
  palette: {
    primary: {
      main: '#00838f',
    },
    secondary: {
      main: '#e53935',
    },
    type: 'dark',
  },
  custom: {
    borderRadius: {
      circle: '50%',
    },
    hover: {
      active: hexToRGB('#00838f', 0.6),
      opacity: 0.6,
    },
    backdrop: {
      backgroundColor: 'rgba(48, 48, 48, 0.5)',
      backgroundImage:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
    },
  },
});

// https://material-ui.com/customization/typography/#responsive-font-sizes
theme = responsiveFontSizes(theme, { factor: 3 });

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
