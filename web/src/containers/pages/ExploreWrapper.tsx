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
import { peopleFetchSuccess } from '../../actions/people/get_people';
import { ApiPerson, PersonFactory } from '../../types/v2/Person';

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
      // If we're filtering on people, we have to fetch them.
      const filterParams = parseFilterParamsFromObject(parsedQueryObj);

      let token = await currentUserJwt();

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
      let response: TeletrackerResponse<ApiItem[]> = await TeletrackerApi.instance.getItems(
        token,
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
