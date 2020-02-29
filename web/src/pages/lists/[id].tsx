import Auth, { CognitoUser } from '@aws-amplify/auth';
import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import { ApiList, List, ListFactory } from '../../types';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { ListRetrieveSuccess } from '../../actions/lists';
import ListDetail from '../../containers/ListDetail';
import Head from 'next/head';
import { currentUserJwt } from '../../utils/page-utils';

interface Props {
  list?: List;
  pageProps: any;
  error?: number;
}

function ListDetailWrapper(props: Props) {
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
    let token = await currentUserJwt();

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
