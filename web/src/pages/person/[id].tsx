import Head from 'next/head';
import React from 'react';
import { personFetchSuccess } from '../../actions/people/get_person';
import PersonDetail, {
  DEFAULT_CREDITS_FILTERS,
} from '../../containers/PersonDetail';
import { ApiPerson, PersonFactory } from '../../types/v2/Person';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { useRouter } from 'next/router';
import _ from 'lodash';
import usePerson from '../../hooks/usePerson';
import { personCreditsFetchSuccess } from '../../actions/people/get_credits';
import { currentUserJwt } from '../../utils/page-utils';
import { ItemFactory } from '../../types/v2/Item';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';
import { ApiItem } from '../../types/v2';

interface Props {
  personId?: string;
  pageProps: any;
}

function PersonDetailWrapper(props: Props) {
  const router = useRouter();

  const requestedId = router.query.id;
  let actualId: string = _.isArray(requestedId) ? requestedId[0] : requestedId;

  const person = usePerson(actualId, undefined);

  return (
    <React.Fragment>
      <Head>
        {/* <title>{props.person ? props.person.name : 'Not Found'}</title> */}
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
        {person ? (
          <React.Fragment>
            <title>{`${person?.name} | Teletracker`}</title>
            <meta
              name="title"
              property="og:title"
              content={`${person?.name} | Where to stream, rent, or buy. Track this person today!`}
            />
            <meta
              name="description"
              property="og:description"
              content={`Find out where to stream, rent, or buy content featuring ${person?.name} online. Track it to find out when it's available on one of your services.`}
            />
            <meta
              name="image"
              property="og:image"
              content={`https://image.tmdb.org/t/p/w342${person?.profile_path}`}
            />
            <meta property="og:type" content="video.movie" />
            <meta property="og:image:type" content="image/jpg" />
            <meta property="og:image:width" content="342" />
            <meta
              data-react-helmet="true"
              property="og:image:height"
              content="513"
            />
            <meta
              data-react-helmet="true"
              name="twitter:card"
              content="summary"
            />
            <meta
              name="twitter:title"
              content={`${person?.name} - Where to Stream, Rent, or Buy their content`}
            />
            <meta
              name="twitter:description"
              content={`Find out where to stream, rent, or buy content featuring ${person?.name} online. Track it to find out when it's available on one of your services.`}
            />
            <meta
              name="twitter:image"
              content={`https://image.tmdb.org/t/p/w342${person?.profile_path}`}
            />
            <meta
              name="keywords"
              content={`${person?.name}, stream, streaming, rent, buy, watch, track`}
            />
          </React.Fragment>
        ) : null}
        <link
          rel="canonical"
          href={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.pathname}`}
        />
        <meta
          property="og:url"
          content={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.pathname}`}
        />
      </Head>
      <PersonDetail preloaded={!_.isUndefined(person)} />
    </React.Fragment>
  );
}

PersonDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url).query || '');
    const filterParams = _.extend(
      { ...DEFAULT_CREDITS_FILTERS, people: [ctx.query.id] },
      parseFilterParamsFromObject(parsedQueryObj),
    );

    const token = await currentUserJwt();

    const personResponsePromise: Promise<TeletrackerResponse<
      ApiPerson
    >> = TeletrackerApi.instance.getPerson(
      token,
      ctx.query.id,
      /*creditsLimit=*/ 0,
    );

    const creditsResponsePromise: Promise<TeletrackerResponse<
      ApiItem[]
    >> = TeletrackerApi.instance.getPersonCredits(
      token,
      ctx.query.id,
      filterParams,
      6,
      undefined,
      ['cast'],
    );

    const [personResponse, creditsResponse] = await Promise.all([
      personResponsePromise,
      creditsResponsePromise,
    ]);

    if (personResponse.ok && creditsResponse.ok) {
      const apiPerson = personResponse.data!.data;
      const credits = creditsResponse.data!.data;
      const fullPerson = PersonFactory.create(apiPerson);
      const creditItems = _.flatMap(credits, c =>
        c ? [ItemFactory.create(c)] : [],
      );

      await Promise.all([
        ctx.store.dispatch(
          personFetchSuccess({
            person: fullPerson,
            rawPerson: apiPerson,
          }),
        ),

        ctx.store.dispatch(
          personCreditsFetchSuccess({
            personId: fullPerson.id,
            credits: creditItems,
            paging: creditsResponse.data!.paging,
            append: false,
            forFilters: filterParams,
          }),
        ),
      ]);

      return {
        personId: fullPerson.id,
      };
    } else if (!personResponse.ok) {
      return {
        error: personResponse.status,
      };
    } else {
      return {
        error: creditsResponse.status,
      };
    }
  } else {
    return {};
  }
};

export default PersonDetailWrapper;
