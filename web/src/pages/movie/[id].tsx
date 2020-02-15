import Auth, { CognitoUser } from '@aws-amplify/auth';
import Head from 'next/head';
import React from 'react';
import { itemFetchSuccess } from '../../actions/item-detail';
import AppWrapper from '../../containers/AppWrapper';
import ItemDetail from '../../containers/ItemDetail';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';

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
      <AppWrapper>
        <ItemDetail />
      </AppWrapper>
    </React.Fragment>
  );
}

ItemDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let response: TeletrackerResponse<ApiItem> = await TeletrackerApi.instance.getItem(
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined,
      ctx.query.id,
      'movie',
    );

    if (response.ok) {
      await ctx.store.dispatch(itemFetchSuccess(response.data!.data));

      return {
        item: ItemFactory.create(response.data!.data),
      };
    } else {
      return {
        error: response.status,
      };
    }
  } else {
    return {};
  }
};

export default ItemDetailWrapper;
