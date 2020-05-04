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
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';

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
    listName = listsById[listId]?.name;
  }

  return (
    <React.Fragment>
      <Head>
        <title>{listName !== '' ? 'List - ' + listName : 'Not Found'}</title>
      </Head>
      <AppWrapper>
        <WithItemFilters>
          <ListDetail preloaded />
        </WithItemFilters>
      </AppWrapper>
    </React.Fragment>
  );
}

ListDetailWrapper.getInitialProps = async ctx => {
  if (ctx.req) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url).query || '');
    const filterParams = parseFilterParamsFromObject(parsedQueryObj);

    let token = await currentUserJwt();

    let response: TeletrackerResponse<ApiList> = await TeletrackerApi.instance.getList(
      token,
      ctx.query.id,
      filterParams.sortOrder,
      true,
      filterParams.itemTypes,
      filterParams.genresFilter,
      undefined,
      filterParams.networks,
      6,
    );

    if (response.ok) {
      await ctx.store.dispatch(
        ListRetrieveSuccess({
          list: ListFactory.create(response.data!.data),
          paging: response.data!.paging,
          append: false,
          forFilters: filterParams,
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
