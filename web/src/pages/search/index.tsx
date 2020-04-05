import { NextPageContext } from 'next';
import Head from 'next/head';
import React from 'react';
import { Store } from 'redux';
import {
  PreloadSearchInitiated,
  SearchFailed,
  SearchSuccess,
} from '../../actions/search';
import AppWrapper from '../../containers/AppWrapper';
import Search from '../../containers/Search';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { currentUserJwt } from '../../utils/page-utils';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';

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
        {/* TODO: Hook this up */}
        <Search inViewportChange={() => {}} />
      </AppWrapper>
    </React.Fragment>
  );
}

SearchWrapper.getInitialProps = async (ctx: NextPageContext & WithStore) => {
  if (ctx.req) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url || '').query || '');
    const filterParams = parseFilterParamsFromObject(parsedQueryObj);
    let query = ctx.query.q as string;

    await ctx.store.dispatch(
      PreloadSearchInitiated({
        query,
      }),
    );

    console.log(filterParams);

    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.search(
      await currentUserJwt(),
      query,
      undefined,
      18,
      filterParams.itemTypes,
      filterParams.networks,
      filterParams.genresFilter,
      filterParams.sliders?.releaseYear,
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
