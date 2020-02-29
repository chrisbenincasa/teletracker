import Auth, { CognitoUser } from '@aws-amplify/auth';
import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import { ApiList, List, ListFactory } from '../../types';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { ListRetrieveSuccess } from '../../actions/lists';
import ListDetail from '../../containers/ListDetail';
import Head from 'next/head';
import { currentUserJwt } from '../../utils/page-utils';
import useStateSelector from '../../hooks/useStateSelector';
import { useRouter } from 'next/router';

interface Props {
  list?: List;
  pageProps: any;
  error?: number;
}

function ListDetailWrapper(props: Props) {
  const router = useRouter();
  const listsById = useStateSelector(state => state.lists.listsById);


  const listId = router.query.id as string;
  console.log('ListDetailWrapper', listsById[listId])

  let listName: string;
  if (props.list) {
    listName = props.list.name;
  } else {
    listName = listsById[listId]?.name
  }

  return (
    <React.Fragment>
      <Head>
        <title>{listName !== '' ? 'List - ' + listName : 'Not Found'}</title>
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
