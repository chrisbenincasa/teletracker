import Auth, { CognitoUser } from '@aws-amplify/auth';
import { NextPageContext } from 'next';
import Head from 'next/head';
import React from 'react';
import { Store } from 'redux';
import {
  SearchFailed,
  SearchSuccess,
  PreloadSearchInitiated,
} from '../../actions/search';
import AppWrapper from '../../containers/AppWrapper';
import Search from '../../containers/Search';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';

interface Props {}

interface WithStore {
  store: Store;
}

function SearchWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Popular</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <AppWrapper>
        <Search inViewportChange={() => {}} />
      </AppWrapper>
    </React.Fragment>
  );
}

SearchWrapper.getInitialProps = async (ctx: NextPageContext & WithStore) => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let query = ctx.query.q as string;

    await ctx.store.dispatch(
      PreloadSearchInitiated({
        query,
      }),
    );

    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.searchV2(
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined,
      query,
      undefined,
      18,
    );

    if (response.ok) {
      await ctx.store.dispatch(
        SearchSuccess({
          results: response.data!.data.map(ItemFactory.create),
          paging: response.data!.paging,
          append: false,
        }),
      );

      return {
        searchResults: response.data!.data,
      };
    } else {
      await ctx.store.dispatch(SearchFailed(new Error('bad')));
      return {
        searchResults: null,
      };
    }
  } else {
    return {};
  }
};

export default SearchWrapper;
