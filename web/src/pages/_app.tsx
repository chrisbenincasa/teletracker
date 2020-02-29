import Amplify from '@aws-amplify/core';
import { MuiThemeProvider } from '@material-ui/core';
import CssBaseline from '@material-ui/core/CssBaseline';
import cookie from 'cookie';
import _ from 'lodash';
import { InitStoreOptions, NextJSAppContext } from 'next-redux-wrapper';
import App, { AppContext } from 'next/app';
import Head from 'next/head';
import React from 'react';
import { Provider } from 'react-redux';
import { Store } from 'redux';
import 'sanitize.css/sanitize.css';
import createStore from '../store';
import theme from '../theme';

interface Config {
  serializeState?: (any) => any;
  deserializeState?: (any) => any;
  storeKey?: string;
  debug?: boolean;
  overrideIsServer?: boolean;
}

const isServer = typeof window === 'undefined';

if (isServer) {
  (global as any).fetch = require('node-fetch');
}

Amplify.configure({
  Auth: {
    region: process.env.REACT_APP_AUTH_REGION,
    userPoolId: process.env.REACT_APP_USER_POOL_ID,
    userPoolWebClientId: process.env.REACT_APP_USER_POOL_CLIENT_ID,
    mandatorySignIn: false,

    cookieStorage: {
      domain: process.env.REACT_APP_COOKIE_DOMAIN,
      path: '/',
    },

    // storage: {
    //   store: {},
    //   getItem(key: string) {
    //     return parsedCookies[key];
    //   },
    //   setItem(_key: string, _value: string) {
    //     throw new Error('auth storage `setItem` not implemented');
    //   },
    //   removeItem(_key) {
    //     throw new Error('auth storage `removeItem` not implemented');
    //   },
    //   clear() {
    //     throw new Error('auth storage `clear` not implemented');
    //   },
    // },

    oauth: {
      domain: process.env.REACT_APP_AUTH_DOMAIN,
      scope: ['email', 'openid'],
      redirectSignIn: process.env.REACT_APP_AUTH_REDIRECT_URI,
      redirectSignOut: process.env.REACT_APP_AUTH_REDIRECT_SIGNOUT_URI,
      responseType: 'code',
    },
  },
});

const initStore = ({ initialState }: InitStoreOptions): Store => {
  const createStoreInner = () => createStore(initialState).store;

  if (isServer) {
    return createStoreInner();
  }

  // Memoize store if client
  if (!('__NEXT_REDUX_STORE__' in window)) {
    window['__NEXT_REDUX_STORE__'] = createStoreInner();
  }

  return (window as any)['__NEXT_REDUX_STORE__'];
};

export interface WrappedAppProps {
  initialProps: any; // stuff returned from getInitialProps
  initialState: any; // stuff in the Store state after getInitialProps
  isServer: boolean;
}

export default class MyApp extends App {
  public static getInitialProps = async (appCtx: AppContext) => {
    if (!appCtx) throw new Error('No app context');
    if (!appCtx.ctx) throw new Error('No page context');

    const cookieString = appCtx.ctx.req
      ? appCtx.ctx.req.headers.cookie
      : !_.isUndefined(document)
      ? document.cookie
      : '';
    let parsedCookies = {};
    if (cookieString) {
      parsedCookies = cookie.parse(cookieString);
    }

    Amplify.configure({
      Auth: {
        storage: {
          store: {},
          getItem(key) {
            return parsedCookies[key];
          },
          setItem(_key, _value) {
            throw new Error('auth storage `setItem` not implemented');
          },
          removeItem(_key) {
            throw new Error('auth storage `removeItem` not implemented');
          },
          clear() {
            throw new Error('auth storage `clear` not implemented');
          },
        },
      },
    });

    const store = initStore({});

    // if (config.debug)
    // console.log('1. WrappedApp.getInitialProps wrapper got the store with state', store.getState());

    // @ts-ignore
    appCtx.ctx.store = store;
    // @ts-ignore
    appCtx.ctx.isServer = isServer;

    let initialProps = {};

    let pageProps = {};
    if (appCtx.Component.getInitialProps) {
      pageProps = await appCtx.Component.getInitialProps(appCtx.ctx);
    }

    // if (config.debug) console.log('3. WrappedApp.getInitialProps has store state', store.getState());

    return {
      isServer,
      initialState: store.getState(),
      initialProps,
      pageProps,
    };
  };

  protected store: Store;

  public constructor(props, context) {
    super(props, context);

    const { initialState } = props;

    this.store = initStore({
      initialState: {
        ...initialState,
        startup: {
          isBooting: true,
        },
      },
    });
  }

  componentDidMount() {
    // Remove the server-side injected CSS.
    const jssStyles = document.querySelector('#jss-server-side');
    if (jssStyles && jssStyles.parentElement) {
      jssStyles.parentElement.removeChild(jssStyles);
    }
  }

  render() {
    const { Component, pageProps } = this.props;

    return (
      <React.Fragment>
        <Head>
          <title>My page</title>
          <meta
            name="viewport"
            content="minimum-scale=1, initial-scale=1, width=device-width"
          />
          <link
            rel="stylesheet"
            href="https://fonts.googleapis.com/css?family=Roboto:300,400,500"
          />
          <link
            rel="stylesheet"
            href="https://fonts.googleapis.com/icon?family=Material+Icons"
          />
          <meta httpEquiv="Accept-CH" content="DPR, Viewport-Width, Width" />
        </Head>
        <Provider store={this.store}>
          {/* <PersistGate loading={null} persistor={persistor}> */}
          {/* <ConnectedRouter history={history}> */}
          <MuiThemeProvider theme={theme}>
            <div>
              <CssBaseline />
              <Component {...pageProps} store={this.store} />
            </div>
          </MuiThemeProvider>
          {/* </ConnectedRouter> */}
          {/* </PersistGate> */}
        </Provider>
      </React.Fragment>
    );
  }
}
