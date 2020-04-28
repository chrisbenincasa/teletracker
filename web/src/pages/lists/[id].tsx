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
import dequal from 'dequal';
import WithItemFilters from '../../components/Filters/FilterContext';

interface Props {
  list?: List;
  pageProps: any;
  error?: number;
}

function ListDetailWrapper(props: Props) {
  const router = useRouter();
  const listsById = useStateSelector(state => state.lists.listsById, dequal);

  const listId = router.query.id as string;

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
        <WithItemFilters>
          <ListDetail />
        </WithItemFilters>
      </AppWrapper>
    </React.Fragment>
  );
}

ListDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    let token = await currentUserJwt();

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
      return {
        error: response.status,
      };
    }
  } else {
    return {};
  }
};

export default ListDetailWrapper;
