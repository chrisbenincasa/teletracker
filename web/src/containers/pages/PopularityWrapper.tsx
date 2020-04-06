import React from 'react';
import Head from 'next/head';
import AppWrapper from '../AppWrapper';
import Popular from '../Popular';
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

function PopularityWrapper() {
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
    let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getPopular(
      token,
      {
        itemTypes: filterParams.itemTypes,
        networks: filterParams.networks,
        sort: filterParams.sortOrder || 'popularity',
        limit: DEFAULT_POPULAR_LIMIT,
        genres: filterParams.genresFilter,
        releaseYearRange: filterParams.sliders?.releaseYear,
        castIncludes: filterParams.people,
        imdbRating: filterParams.sliders?.imdbRating,
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
