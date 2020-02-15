import Head from 'next/head';
import React from 'react';
import { itemFetchSuccess } from '../../src/actions/item-detail';
import AppWrapper from '../../src/containers/AppWrapper';
import { ApiItem } from '../../src/types/v2';
import { Item, ItemFactory } from '../../src/types/v2/Item';
import {
  TeletrackerApi,
  TeletrackerResponse,
} from '../../src/utils/api-client';
import { extractApiKeyFromCookie } from '../../src/utils/header-utils';
import ItemDetail from '../../src/containers/ItemDetail';
import { useRouter } from 'next/router';
import Auth from '@aws-amplify/auth';

interface Props {
  item?: Item;
  pageProps: any;
}

function ItemDetailWrapper(props: Props) {
  const router = useRouter();
  return (
    <React.Fragment>
      {/* <Provider store={store}>
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
      </Provider> */}
      <AppWrapper>
        <ItemDetail />
      </AppWrapper>
    </React.Fragment>
  );
}

ItemDetailWrapper.getInitialProps = async ctx => {
  let accessToken: string | undefined;
  if (ctx.req && ctx.req.headers.cookie) {
    accessToken = extractApiKeyFromCookie(ctx.req.headers.cookie);
  }

  let user;
  try {
    user = await Auth.currentUserPoolUser({ bypassCache: true });
  } catch (e) {
    console.error(e);
  }

  console.log(user);

  let response: TeletrackerResponse<ApiItem> = await TeletrackerApi.instance.getItem(
    accessToken,
    ctx.query.id,
    'show',
  );

  if (response.ok) {
    console.log('success', response.data!.data);
    await ctx.store.dispatch(itemFetchSuccess(response.data!.data));

    return {
      item: ItemFactory.create(response.data!.data),
    };
  } else {
    return {
      error: response.status,
    };
  }
};

export default ItemDetailWrapper;
