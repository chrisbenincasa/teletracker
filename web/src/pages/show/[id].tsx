import Auth, { CognitoUser } from '@aws-amplify/auth';
import { useRouter } from 'next/router';
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
  const router = useRouter();
  return (
    <React.Fragment>
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
      'show',
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
