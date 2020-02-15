import Auth, { CognitoUser } from '@aws-amplify/auth';
import Head from 'next/head';
import React from 'react';
import { personFetchSuccess } from '../../actions/people/get_person';
import AppWrapper from '../../containers/AppWrapper';
import PersonDetail from '../../containers/PersonDetail';
import { ApiPerson } from '../../types/v2';
import { Person, PersonFactory } from '../../types/v2/Person';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { useRouter } from 'next/router';

interface Props {
  person?: Person;
  pageProps: any;
}

function PersonDetailWrapper(props: Props) {
  const router = useRouter();
  return (
    <React.Fragment>
      <Head>
        {/* <title>{props.person ? props.person.name : 'Not Found'}</title> */}
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
        {props.person ? (
          <React.Fragment>
            <title>{`${props.person.name} | Teletracker`}</title>
            <meta
              name="title"
              property="og:title"
              content={`${props.person.name} | Where to stream, rent, or buy. Track this person today!`}
            />
            <meta
              name="description"
              property="og:description"
              content={`Find out where to stream, rent, or buy content featuring ${props.person.name} online. Track it to find out when it's available on one of your services.`}
            />
            <meta
              name="image"
              property="og:image"
              content={`https://image.tmdb.org/t/p/w342${props.person.profile_path}`}
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
              content={`${props.person.name} - Where to Stream, Rent, or Buy their content`}
            />
            <meta
              name="twitter:description"
              content={`Find out where to stream, rent, or buy content featuring ${props.person.name} online. Track it to find out when it's available on one of your services.`}
            />
            <meta
              name="twitter:image"
              content={`https://image.tmdb.org/t/p/w342${props.person.profile_path}`}
            />
            <meta
              name="keywords"
              content={`${props.person.name}, stream, streaming, rent, buy, watch, track`}
            />
          </React.Fragment>
        ) : null}
        }
        <link
          rel="canonical"
          href={`http://teletracker.com${router.pathname}`}
        />
        <meta
          property="og:url"
          content={`http://teletracker.com${router.pathname}`}
        />
      </Head>
      <AppWrapper>
        <PersonDetail />
      </AppWrapper>
    </React.Fragment>
  );
}

PersonDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let response: TeletrackerResponse<ApiPerson> = await TeletrackerApi.instance.getPerson(
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined,
      ctx.query.id,
    );

    if (response.ok) {
      const fullPerson = PersonFactory.create(response.data!.data);

      await ctx.store.dispatch(personFetchSuccess(fullPerson));

      return {
        person: fullPerson,
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
