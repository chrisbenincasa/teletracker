import Auth, { CognitoUser } from '@aws-amplify/auth';
import Head from 'next/head';
import React from 'react';
import { personFetchSuccess } from '../../actions/people/get_person';
import AppWrapper from '../../containers/AppWrapper';
import PersonDetail from '../../containers/PersonDetail';
import { ApiPerson, PersonFactory } from '../../types/v2/Person';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { useRouter } from 'next/router';
import _ from 'lodash';
import usePerson from '../../hooks/usePerson';
import { personCreditsFetchSuccess } from '../../actions/people/get_credits';
import { currentUserJwt } from '../../utils/page-utils';
import { ItemFactory } from '../../types/v2/Item';

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
        }
        <link
          rel="canonical"
          href={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.pathname}`}
        />
        <meta
          property="og:url"
          content={`${process.env.REACT_APP_TELETRACKER_BASE_URL}${router.pathname}`}
        />
      </Head>
      <AppWrapper>
        <PersonDetail preloaded={!_.isUndefined(person)} />
      </AppWrapper>
    </React.Fragment>
  );
}

PersonDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let response: TeletrackerResponse<ApiPerson> = await TeletrackerApi.instance.getPerson(
      await currentUserJwt(),
      ctx.query.id,
      /*creditsLimit=*/ 6,
    );

    if (response.ok) {
      let apiPerson = response.data!.data;
      const fullPerson = PersonFactory.create(apiPerson);
      const creditItems = _.flatMap(apiPerson.cast_credits?.data || [], c =>
        c.item ? [ItemFactory.create(c.item!)] : [],
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
            paging: apiPerson.cast_credits?.paging,
            append: false,
          }),
        ),
      ]);

      return {
        personId: fullPerson.id,
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

export default PersonDetailWrapper;
