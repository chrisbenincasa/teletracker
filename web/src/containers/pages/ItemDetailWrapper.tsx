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

interface Props {
  // item?: Item;
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

    const item = useItem(actualId, undefined);

    return (
      <React.Fragment>
        <Head>
          <title>{windowTitleForItem(actualId, item, itemsById)}</title>
          <meta
            name="title"
            property="og:title"
            content={`${item?.canonicalTitle} | Where to stream, rent, or buy. Track it today!`}
          />
          <meta
            name="description"
            property="og:description"
            content={`Find out where to stream, rent, or buy ${item?.canonicalTitle} online. Track it to find out when it's available on one of your services.`}
          />
          {/* TODO FIX <meta
            name="image"
            property="og:image"
            content={`https://image.tmdb.org/t/p/w342${itemDetail.posterPath}`}
          /> */}
          <meta property="og:type" content="video.movie" />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content="342" />
          <meta
            data-react-helmet="true"
            property="og:image:height"
            content="513"
          />
          <meta
            property="og:url"
            content={`http://teletracker.com${router.asPath}`}
          />
          <meta
            data-react-helmet="true"
            name="twitter:card"
            content={`Find out where to stream, rent, or buy ${item?.canonicalTitle} online. Track it to find out when it's available on one of your services.`}
          />
          <meta
            name="twitter:title"
            content={`${item?.canonicalTitle} - Where to Stream, Rent, or Buy It Online`}
          />
          <meta
            name="twitter:description"
            content={`Find out where to stream, rent, or buy ${item?.canonicalTitle} online. Track it to find out when it's available on one of your services.`}
          />

          {/* TODO FIX <meta
            name="twitter:image"
            content={`https://image.tmdb.org/t/p/w342${itemDetail.posterPath}`}
          /> */}
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
          <ItemDetail />
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
          // item: ItemFactory.create(response.data.data),
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