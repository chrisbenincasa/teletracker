import Head from 'next/head';
import React from 'react';
import { popularFailed, popularSuccess } from '../../actions/popular';
import Popular from '../../containers/Popular';
import { ApiItem } from '../../types/v2';
import { ItemFactory } from '../../types/v2/Item';
import AppWrapper from '../../containers/AppWrapper';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { currentUserJwt } from '../../utils/page-utils';
import qs from 'querystring';
import url from 'url';
import {
  parseFilterParams,
  parseFilterParamsFromObject,
} from '../../utils/urlHelper';
import { DEFAULT_POPULAR_LIMIT } from '../../constants';

interface Props {
  pageProps: any;
}

function PopularityWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Popular</title>
      </Head>
      <AppWrapper>
        <Popular />
      </AppWrapper>
    </React.Fragment>
  );
}

PopularityWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url).query || '');
    const filterParams = parseFilterParamsFromObject(parsedQueryObj);

    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getPopular(
      await currentUserJwt(),
      undefined,
      filterParams.itemTypes,
      filterParams.networks,
      undefined,
      filterParams.sortOrder,
      DEFAULT_POPULAR_LIMIT,
      filterParams.genresFilter,
      filterParams.sliders?.releaseYear,
    );

    if (response.ok && response.data) {
      await ctx.store.dispatch(
        popularSuccess({
          popular: response.data.data.map(ItemFactory.create),
          paging: response.data.paging,
          append: false,
        }),
      );

      return {
        popularItems: response.data!.data,
      };
    } else {
      await ctx.store.dispatch(
        popularFailed(new Error(response.problem?.toString())),
      );
      return {
        popularItems: null,
      };
    }
  } else {
    return {};
  }
};

export default PopularityWrapper;
