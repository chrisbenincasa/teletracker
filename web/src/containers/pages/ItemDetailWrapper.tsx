import Head from 'next/head';
import React from 'react';
import { itemFetchSuccess } from '../../actions/item-detail';
import AppWrapper from '../../containers/AppWrapper';
import ItemDetail from '../../containers/ItemDetail';
import { ApiItem } from '../../types/v2';
import { Item, ItemFactory } from '../../types/v2/Item';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { useSelector } from 'react-redux';
import { AppState } from '../../reducers';
import { useRouter } from 'next/router';
import _ from 'lodash';
import { windowTitleForItem } from '../../utils/item-utils';
import { currentUserJwt } from '../../utils/page-utils';
import { ItemType } from '../../types';
import useItem from '../../hooks/useItem';
import moment from 'moment';

interface Props {
  itemId?: string;
  pageProps: any;
}

export default function makeItemDetailWrapper(type: ItemType) {
  function ItemDetailWrapper(props: Props) {
    const router = useRouter();
    const itemsById = useSelector(
      (state: AppState) => state.itemDetail.thingsById,
    );

    const requestedId = router.query.id;
    let actualId: string = _.isArray(requestedId)
      ? requestedId[0]
      : requestedId;

    // TODO: Handle not found here...
    const item = useItem(actualId, undefined);
    const releaseDate =
      (item?.release_date && moment(item?.release_date).format('YYYY')) || '';
    const firstLineOverview = item?.overview?.split('.')[0];

    return (
      <React.Fragment>
        <Head>
          <title>{windowTitleForItem(actualId, item, itemsById)}</title>
          <meta
            name="title"
            property="og:title"
            content={`${item?.canonicalTitle} (${releaseDate}) | Where to stream, rent, or buy`}
          />
          <meta
            name="description"
            property="og:description"
            content={`${firstLineOverview}. Find out where to stream, rent, or buy ${item?.canonicalTitle} on Teletracker.tv.`}
          />
          <meta
            name="image"
            property="og:image"
            content={`https://image.tmdb.org/t/p/w780${item?.backdropImage?.id}`}
          />
          <meta
            property="og:type"
            content={item?.type === 'movie' ? 'video.movie' : 'video.tv_show'}
          />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content="780" />
          <meta property="og:image:height" content="439" />
          <meta
            property="og:url"
            content={`http://teletracker.com${router.asPath}`}
          />
          <meta name="twitter:card" content="summary_large_image" />
          <meta
            name="twitter:title"
            content={`${item?.canonicalTitle} - Where to Stream, Rent, or Buy It Online`}
          />
          <meta
            name="twitter:description"
            content={`${firstLineOverview}. Find out where to stream, rent, or buy ${item?.canonicalTitle} on Teletracker.tv.`}
          />
          <meta
            name="twitter:image"
            content={`https://image.tmdb.org/t/p/w780${item?.backdropImage?.id}`}
          />
          <meta name="twitter:domain" content="Teletracker.tv" />
          <meta
            name="keywords"
            content={`${item?.canonicalTitle}, ${item?.type}, stream, streaming, rent, buy, watch, track`}
          />
          <link
            rel="canonical"
            href={`http://teletracker.com${router.asPath}`}
          />
        </Head>
        <AppWrapper>
          <ItemDetail
            itemPreloadedFromServer={props.itemId && !_.isUndefined(item)}
          />
        </AppWrapper>
      </React.Fragment>
    );
  }

  ItemDetailWrapper.getInitialProps = async ctx => {
    if (ctx.req) {
      let response: TeletrackerResponse<ApiItem> = await TeletrackerApi.instance.getItem(
        await currentUserJwt(),
        ctx.query.id,
        type,
      );

      if (response.ok && response.data) {
        await ctx.store.dispatch(itemFetchSuccess(response.data.data));

        return {
          itemId: response.data.data.id,
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

  return ItemDetailWrapper;
}
