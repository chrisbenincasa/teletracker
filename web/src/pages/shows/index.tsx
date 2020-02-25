import Auth, { CognitoUser } from '@aws-amplify/auth';
import { NextPageContext } from 'next';
import Head from 'next/head';
import React from 'react';
import { Store } from 'redux';
import { SearchFailed, SearchSuccess } from '../../actions/search';
import AppWrapper from '../../containers/AppWrapper';
import Search from '../../containers/Search';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import Explore from '../../containers/Explore';
import { exploreSuccess, exploreFailed } from '../../actions/explore';

interface Props {}

interface WithStore {
  store: Store;
}

function ExploreWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Explore</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <AppWrapper>
        <Explore />
      </AppWrapper>
    </React.Fragment>
  );
}

ExploreWrapper.getInitialProps = async (ctx: NextPageContext & WithStore) => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getItems(
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
      20,
    );

    if (response.ok) {
      await ctx.store.dispatch(
        exploreSuccess({
          items: response.data!.data.map(ItemFactory.create),
          paging: response.data!.paging,
          append: false,
        }),
      );

      return {
        searchResults: response.data!.data,
      };
    } else {
      await ctx.store.dispatch(exploreFailed(new Error('bad')));
      return {
        searchResults: null,
      };
    }
  } else {
    return {};
  }
};

export default ExploreWrapper;
