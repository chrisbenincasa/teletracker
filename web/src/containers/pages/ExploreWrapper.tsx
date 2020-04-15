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

export default function makeExploreWrapper(defaultFilters: FilterParams) {
  function ExploreWrapper() {
    return (
      <React.Fragment>
        <Head>
          <title>Explore - Popular</title>
        </Head>
        <AppWrapper>
          <Explore defaultFilters={defaultFilters} />
        </AppWrapper>
      </React.Fragment>
    );
  }

  ExploreWrapper.getInitialProps = async ctx => {
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
      let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getItems(
        token,
        {
          itemTypes: filterParams.itemTypes || defaultFilters.itemTypes,
          networks: filterParams.networks,
          sort:
            filterParams.sortOrder || defaultFilters.sortOrder || 'rating|imdb',
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

  return ExploreWrapper;
}
