import { launchUri } from '@aws-amplify/auth/lib/OAuth/urlOpener';
import Amplify from '@aws-amplify/core';
import { MuiThemeProvider } from '@material-ui/core';
import CssBaseline from '@material-ui/core/CssBaseline';
import { InitStoreOptions, NextJSAppContext } from 'next-redux-wrapper';
import App from 'next/app';
import Head from 'next/head';
import React from 'react';
import { Provider } from 'react-redux';
import { Store } from 'redux';
import 'sanitize.css/sanitize.css';
import Footer from '../src/components/Footer';
import createStore from '../src/store';
import theme from '../src/theme';
import cookie from 'cookie';
import _ from 'lodash';

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
      domain: '.teletracker.local',
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

const initStore = ({ initialState, ctx }: InitStoreOptions): Store => {
  const createStoreInner = () => createStore(initialState).store;

  if (isServer) {
    console.log('isServer=true. create new store');
    return createStoreInner();
  }

  // Memoize store if client
  if (!('__NEXT_REDUX_STORE__' in window)) {
    console.log(window['__NEXT_REDUX_STORE__']);
    window['__NEXT_REDUX_STORE__'] = createStoreInner();
  }

  return (window as any)['__NEXT_REDUX_STORE__'];
};

export interface WrappedAppProps {
  initialProps: any; // stuff returned from getInitialProps
  initialState: any; // stuff in the Store state after getInitialProps
  isServer: boolean;
}

export default class MyApp extends App<WrappedAppProps> {
  public static getInitialProps = async (appCtx: NextJSAppContext) => {
    console.log('App.getInitialProps start');
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
            console.log(key);
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

    const store = initStore({
      ctx: appCtx.ctx,
    });

    // if (config.debug)
    // console.log('1. WrappedApp.getInitialProps wrapper got the store with state', store.getState());

    appCtx.ctx.store = store;
    appCtx.ctx.isServer = isServer;

    let initialProps = {};

    let pageProps = {};
    if (appCtx.Component.getInitialProps) {
      pageProps = await appCtx.Component.getInitialProps(appCtx.ctx);
    }

    // if (config.debug) console.log('3. WrappedApp.getInitialProps has store state', store.getState());

    console.log('App.getInitialProps end');
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
      initialState,
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
