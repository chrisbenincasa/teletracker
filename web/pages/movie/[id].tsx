import ItemDetail from '../../src/containers/ItemDetail';
import { TeletrackerApi } from '../../src/utils/api-client';
import React from 'react';
import App from 'next/app';
import Head from 'next/head';
import { MuiThemeProvider } from '@material-ui/core';
import CssBaseline from '@material-ui/core/CssBaseline';
// import 'sanitize.css/sanitize.css';
import theme from '../../src/theme';
import { Provider } from 'react-redux';
import createStore, { history } from '../../src/store';
import { PersistGate } from 'redux-persist/integration/react';
import { ConnectedRouter } from 'connected-react-router';
import Amplify from '@aws-amplify/core';
import { launchUri } from '@aws-amplify/auth/lib/OAuth/urlOpener';
import { ItemFactory, Item } from '../../src/types/v2/Item';

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
        if (url.includes('logout') && document) {
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

export const { store, persistor } = createStore();

interface Props {
  item?: Item;
  pageProps: any;
}

function ItemDetailWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>{props.item ? props.item.original_title : 'Not Found'}</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <Provider store={store}>
        <PersistGate loading={null} persistor={persistor}>
          <ConnectedRouter history={history}>
            <MuiThemeProvider theme={theme}>
              <div>
                <CssBaseline />
                <ItemDetail
                  {...props.pageProps}
                  initialItem={
                    props.item ? ItemFactory.create(props.item) : undefined
                  }
                />
              </div>
            </MuiThemeProvider>
          </ConnectedRouter>
        </PersistGate>
      </Provider>
    </React.Fragment>
  );
}

ItemDetailWrapper.getInitialProps = async ctx => {
  let response = await TeletrackerApi.instance.getItem(
    undefined,
    ctx.query.id,
    'show',
  );
  return {
    item: response.data ? response.data.data : undefined,
  };
};

export default ItemDetailWrapper;
