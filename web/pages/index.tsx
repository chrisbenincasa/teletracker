import Head from 'next/head';
import React from 'react';
import { popularSuccess, popularFailed } from '../src/actions/popular';
import Popular from '../src/containers/Popular';
import { ApiItem } from '../src/types/v2';
import { ItemFactory } from '../src/types/v2/Item';
import AppWrapper from '../src/containers/AppWrapper';
import { TeletrackerApi, TeletrackerResponse } from '../src/utils/api-client';
import Auth from '@aws-amplify/auth';
import { extractApiKeyFromCookie } from '../src/utils/header-utils';
import { Store } from 'redux'

interface Props {
  pageProps: any;
  store: Store
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
  console.log('PopularityWrapper.getInitialProps start');
  const accessToken = extractApiKeyFromCookie(ctx.req.headers.cookie);

  let user;
  try {
    user = await Auth.currentAuthenticatedUser({ bypassCache: true });
  } catch (e) {
    console.log(e);
  }

  console.log(user);

  let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getPopular(
    accessToken,
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

    console.log('PopularityWrapper.getInitialProps end, success');
    return {
      popularItems: response.data!.data,
    };
  } else {
    console.log('PopularityWrapper.getInitialProps end, failed');
    await ctx.store.dispatch(popularFailed(new Error('bad')));
    console.log(response.problem);
    return {
      popularItems: null,
    };
  }
};

export default PopularityWrapper;
