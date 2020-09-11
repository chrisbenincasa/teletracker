import React from 'react';
import Head from 'next/head';
import AppWrapper from '../AppWrapper';
import Explore from '../Explore';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { ApiItem } from '../../types/v2';
import { currentUserJwt } from '../../utils/page-utils';
import { DEFAULT_POPULAR_LIMIT } from '../../constants';
import { popularFailed, popularSuccess } from '../../actions/popular';
import { ItemFactory } from '../../types/v2/Item';
import { peopleFetchSuccess } from '../../actions/people/get_people';
import { ApiPerson, PersonFactory } from '../../types/v2/Person';
import { FilterParams } from '../../utils/searchFilters';
import WithItemFilters from '../../components/Filters/FilterContext';
import _ from 'lodash';
import { useRouter } from 'next/router';

export default function makeExploreWrapper(defaultFilters: FilterParams) {
  function ExploreWrapper() {
    const router = useRouter();
    const domain = process.env.REACT_APP_TELETRACKER_BASE_URL;

    return (
      <React.Fragment>
        <Head>
          <title>🔭 Telescope | Explore</title>
          <meta
            name="title"
            property="og:title"
            content={`Telescope | Find where to stream, rent, or buy anything.`}
          />
          <meta
            name="description"
            property="og:description"
            content={`Find out where to stream, rent, or buy anything on ${domain}.`}
          />
          <meta
            name="image"
            property="og:image"
            content={`${domain}/images/unfurl_screen.jpg`}
          />
          <meta property="og:type" content="website" />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content={'3359'} />
          <meta property="og:image:height" content={'1498'} />
          <meta property="og:url" content={`${domain}${router.asPath}`} />
          <meta name="twitter:card" content={'summary_large_image'} />
          <meta name="twitter:title" content={`Telescope`} />
          <meta
            name="twitter:description"
            content={`Find where to stream, rent, or buy anything on ${domain}.`}
          />
          <meta
            name="twitter:image"
            content={`${domain}/images/unfurl_screen.jpg`}
          />
          <meta name="twitter:domain" content={domain} />
          <meta
            name="keywords"
            content={`Telescope, television, movie, stream, streaming, rent, buy, watch, track, netflix, hulu`}
          />
          <link rel="canonical" href={`${domain}${router.asPath}`} />
        </Head>
        <AppWrapper hideFooter>
          <WithItemFilters initialFilters={{ ...defaultFilters }}>
            <Explore />
          </WithItemFilters>
        </AppWrapper>
      </React.Fragment>
    );
  }

  ExploreWrapper.getInitialProps = async ctx => {
    if (ctx.req) {
      const parsedQueryObj = qs.parse(url.parse(ctx.req.url).query || '');
      const filterParams = _.extend(
        defaultFilters,
        parseFilterParamsFromObject(parsedQueryObj),
      );

      let token = await currentUserJwt();

      // If we're filtering on people, we have to fetch them.
      // TODO: Limit these
      let peoplePromise: Promise<ApiPerson[] | undefined> | undefined;
      if (filterParams.people && filterParams.people.length) {
        peoplePromise = TeletrackerApi.instance
          .getPeople(token, filterParams.people)
          .then(result => {
            if (result.ok && result.data) {
              return result.data.data;
            }
          });
      }

      // Start fetching the items with the given filters
      let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getItems(
        token,
        {
          itemTypes: filterParams.itemTypes,
          networks: filterParams.networks,
          sort: filterParams.sortOrder || 'rating|imdb',
          limit: DEFAULT_POPULAR_LIMIT,
          genres: filterParams.genresFilter,
          releaseYearRange: filterParams.sliders?.releaseYear,
          castIncludes: filterParams.people,
          imdbRating: filterParams.sliders?.imdbRating,
          offerTypes: filterParams?.offers?.types,
        },
      );

      if (response.ok && response.data) {
        // If we were fetching people, wait on that
        if (peoplePromise) {
          let peopleData = await peoplePromise;
          if (peopleData) {
            await ctx.store.dispatch(
              peopleFetchSuccess(peopleData.map(PersonFactory.create)),
            );
          }
        }

        await ctx.store.dispatch(
          popularSuccess({
            popular: response.data.data.map(ItemFactory.create),
            paging: response.data.paging,
            append: false,
            forFilters: filterParams,
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

  return ExploreWrapper;
}
