import Auth, { CognitoUser } from '@aws-amplify/auth';
import { useRouter } from 'next/router';
import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import { ApiList, List, ListFactory } from '../../types';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { ListRetrieveSuccess } from '../../actions/lists';
import ListDetail from '../../containers/ListDetail';
import Head from 'next/head';

interface Props {
  list?: List;
  pageProps: any;
  error?: number;
}

function ListDetailWrapper(props: Props) {
  const router = useRouter();
  return (
    <React.Fragment>
      <Head>
        <title>{props.list ? 'List - ' + props.list.name : 'Not Found'}</title>
      </Head>
      <AppWrapper>
        <ListDetail />
      </AppWrapper>
    </React.Fragment>
  );
}

ListDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let user: CognitoUser | undefined;
    try {
      user = await Auth.currentAuthenticatedUser({ bypassCache: true });
    } catch (e) {
      console.log(e);
    }

    let token =
      user && user.getSignInUserSession()
        ? user
            .getSignInUserSession()!
            .getAccessToken()
            .getJwtToken()
        : undefined;

    if (token) {
      let response: TeletrackerResponse<ApiList> = await TeletrackerApi.instance.getList(
        token,
        ctx.query.id,
      );

      if (response.ok) {
        await ctx.store.dispatch(
          ListRetrieveSuccess({
            list: ListFactory.create(response.data!.data),
            paging: response.data!.paging,
            append: false,
          }),
        );

        return {
          list: ListFactory.create(response.data!.data),
        };
      } else {
        console.log(response.status);
        return {
          error: response.status,
        };
      }
    } else {
    }
  } else {
    return {};
  }
};

export default ListDetailWrapper;
