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
    const domain = process.env.REACT_APP_TELETRACKER_BASE_URL;
    let imageURL: string = '';
    let summaryType: string = '';
    let imageWidth: string = '';
    let imageHeight: string = '';

    if (item?.backdropImage?.id) {
      imageURL = `https://image.tmdb.org/t/p/w780${item?.backdropImage?.id}`;
      summaryType = 'summary_large_image';
      imageWidth = '780';
      imageHeight = '439';
    } else if (item?.posterImage?.id) {
      imageURL = `https://image.tmdb.org/t/p/w500${item?.posterImage?.id}`;
      summaryType = 'summary';
      imageWidth = '500';
      imageHeight = '750';
    } else {
      imageURL = ''; // To do: make a backup share image for when one doesn't exist
      summaryType = 'summary';
      imageWidth = '0';
      imageHeight = '0';
    }

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
            content={`${firstLineOverview}. Find out where to stream, rent, or buy ${item?.canonicalTitle} on ${domain}.`}
          />
          <meta name="image" property="og:image" content={imageURL} />
          <meta
            property="og:type"
            content={item?.type === 'movie' ? 'video.movie' : 'video.tv_show'}
          />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content={imageWidth} />
          <meta property="og:image:height" content={imageHeight} />
          <meta
            property="og:url"
            content={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.asPath}`}
          />
          <meta name="twitter:card" content={summaryType} />
          <meta
            name="twitter:title"
            content={`${item?.canonicalTitle} - Where to Stream, Rent, or Buy It Online`}
          />
          <meta
            name="twitter:description"
            content={`${firstLineOverview}. Find out where to stream, rent, or buy ${item?.canonicalTitle} on ${domain}.`}
          />
          <meta name="twitter:image" content={imageURL} />
          <meta name="twitter:domain" content={domain} />
          <meta
            name="keywords"
            content={`${item?.canonicalTitle}, ${item?.type}, stream, streaming, rent, buy, watch, track`}
          />
          <link
            rel="canonical"
            href={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.asPath}`}
          />
        </Head>
        <AppWrapper>
          <ItemDetail
            itemPreloadedFromServer={
              !_.isUndefined(props.itemId) && !_.isUndefined(item)
            }
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
        console.error(
          'Got error code ' +
            response.status +
            ' while looking up ' +
            ctx.query.id,
        );
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
