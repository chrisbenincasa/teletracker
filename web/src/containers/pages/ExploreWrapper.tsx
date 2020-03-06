import { ItemType } from '../../types';
import { NextPageContext } from 'next';
import Head from 'next/head';
import React from 'react';
import { Store } from 'redux';
import AppWrapper from '../../containers/AppWrapper';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import Explore from '../../containers/Explore';
import { exploreFailed, exploreSuccess } from '../../actions/explore';
import { currentUserJwt } from '../../utils/page-utils';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';

export default function makeExploreWrapper(itemType: ItemType) {
  interface Props {}

  interface WithStore {
    store: Store;
  }

  function ExploreWrapper(props: Props) {
    return (
      <React.Fragment>
        <Head>
          <title>Explore</title>
        </Head>
        <AppWrapper>
          <Explore />
        </AppWrapper>
      </React.Fragment>
    );
  }

  ExploreWrapper.getInitialProps = async (ctx: NextPageContext & WithStore) => {
    if (ctx.req) {
      const parsedQueryObj = qs.parse(url.parse(ctx.req.url || '').query || '');
      const filterParams = parseFilterParamsFromObject(parsedQueryObj);

      let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getItems(
        await currentUserJwt(),
        [itemType],
        filterParams.networks,
        undefined,
        filterParams.sortOrder,
        18,
        filterParams.genresFilter,
        filterParams.sliders?.releaseYear,
        filterParams.people,
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

  return ExploreWrapper;
}
