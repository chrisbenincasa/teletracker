import { NextPageContext } from 'next';
import Head from 'next/head';
import React, { useCallback, useState } from 'react';
import { Store } from 'redux';
import {
  PreloadSearchInitiated,
  SearchFailed,
  SearchSuccess,
} from '../../actions/search';
import Search from '../../containers/Search';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { currentUserJwt } from '../../utils/page-utils';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';
import { DEFAULT_POPULAR_LIMIT } from '../../constants';
import WithItemFilters from '../../components/Filters/FilterContext';
import _ from 'lodash';

interface Props {
  query?: string;
}

interface WithStore {
  store: Store;
}

function SearchWrapper(props: Props) {
  let title = '🔭 Telescope Search';
  if (props.query) {
    title += ' - ' + props.query;
  }

  const [showToolbarSearch, setShowToolbarSearch] = useState(
    _.isUndefined(props.query),
  );

  const inViewportCallback = useCallback((inViewport: boolean) => {
    setShowToolbarSearch(!inViewport);
  }, []);

  return (
    <React.Fragment>
      <Head>
        <title>{title}</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <WithItemFilters>
        <Search
          preloadedQuery={props.query}
          inViewportChange={inViewportCallback}
        />
      </WithItemFilters>
      {/*<AppWrapper hideFooter showToolbarSearch={showToolbarSearch}>*/}
      {/*   TODO: Hook this up */}
      {/*</AppWrapper>*/}
    </React.Fragment>
  );
}

SearchWrapper.getInitialProps = async (ctx: NextPageContext & WithStore) => {
  if (ctx.req) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url || '').query || '');
    const filterParams = parseFilterParamsFromObject(parsedQueryObj);
    let query = ctx.query.q as string | undefined;

    if (query) {
      await ctx.store.dispatch(
        PreloadSearchInitiated({
          query,
        }),
      );

      let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.search(
        await currentUserJwt(),
        {
          searchText: query,
          limit: DEFAULT_POPULAR_LIMIT,
          itemTypes: filterParams.itemTypes,
          networks: filterParams.networks,
          genres: filterParams.genresFilter,
          releaseYearRange: filterParams.sliders?.releaseYear,
          imdbRating: filterParams.sliders?.imdbRating,
        },
      );

      if (response.ok) {
        await ctx.store.dispatch(
          SearchSuccess({
            results: response.data!.data.map(ItemFactory.create),
            paging: response.data!.paging,
            append: false,
            forFilters: filterParams,
          }),
        );

        return {
          query,
        };
      } else {
        await ctx.store.dispatch(SearchFailed(new Error('bad')));
        return {
          query,
        };
      }
    } else {
      return {};
    }
  } else {
    return {};
  }
};

export default SearchWrapper;
