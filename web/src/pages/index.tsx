import Head from 'next/head';
import React from 'react';
import { popularSuccess, popularFailed } from '../actions/popular';
import Popular from '../containers/Popular';
import { ApiItem } from '../types/v2';
import { ItemFactory } from '../types/v2/Item';
import AppWrapper from '../containers/AppWrapper';
import { TeletrackerApi, TeletrackerResponse } from '../utils/api-client';
import Auth from '@aws-amplify/auth';
import { extractApiKeyFromCookie } from '../utils/header-utils';
import { Store } from 'redux';

interface Props {
  pageProps: any;
  store: Store;
}

function PopularityWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Popular</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <AppWrapper store={props.store}>
        <Popular />
      </AppWrapper>
    </React.Fragment>
  );
}

PopularityWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getPopular(
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      20,
    );

    if (response.ok) {
      await ctx.store.dispatch(
        popularSuccess({
          popular: response.data!.data.map(ItemFactory.create),
          paging: response.data!.paging,
          append: false,
        }),
      );

      return {
        popularItems: response.data!.data,
      };
    } else {
      await ctx.store.dispatch(popularFailed(new Error('bad')));
      return {
        popularItems: null,
      };
    }
  } else {
    return {};
  }
};

export default PopularityWrapper;
