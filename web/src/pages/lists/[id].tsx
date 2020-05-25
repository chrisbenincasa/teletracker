import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import { ApiList, ListFactory } from '../../types';
import { TeletrackerApi, TeletrackerResponse } from '../../utils/api-client';
import { ListRetrieveSuccess } from '../../actions/lists';
import ListDetail from '../../containers/ListDetail';
import Head from 'next/head';
import { currentUserJwt } from '../../utils/page-utils';
import useStateSelector from '../../hooks/useStateSelector';
import { useRouter } from 'next/router';
import qs from 'querystring';
import url from 'url';
import { parseFilterParamsFromObject } from '../../utils/urlHelper';
import { FilterParams } from '../../utils/searchFilters';
import { PersonFactory } from '../../types/v2/Person';
import { peopleFetchSuccess } from '../../actions/people/get_people';
import { loadMetadataSuccess } from '../../actions/metadata/load_metadata';
import { smartListRulesToFilters } from '../../utils/list-utils';
import { hookDeepEqual } from '../../hooks/util';
import selectList from '../../selectors/selectList';
import { ApiItem } from '../../types/v2';
import { retrieveListItemsSucceeded } from '../../actions/lists/get_list_items';
import { ItemFactory } from '../../types/v2/Item';

interface Props {
  readonly error?: number;
  readonly preloaded: boolean;
}

function ListDetailWrapper(props: Props) {
  const router = useRouter();

  const listId = router.query.id as string;
  const list = useStateSelector(
    state => selectList(state, listId),
    hookDeepEqual,
  );

  let listName = list?.name || '';

  return (
    <React.Fragment>
      <Head>
        <title>{listName !== '' ? 'List - ' + listName : 'Not Found'}</title>
      </Head>
      <AppWrapper>
        <ListDetail preloaded={props.preloaded} />
      </AppWrapper>
    </React.Fragment>
  );
}

ListDetailWrapper.getInitialProps = async ctx => {
  if (ctx.isServer) {
    const parsedQueryObj = qs.parse(url.parse(ctx.req.url).query || '');
    const filterParams = parseFilterParamsFromObject(parsedQueryObj);

    let token = await currentUserJwt();

    // Load the list and a few items
    let listResponseFut: Promise<TeletrackerResponse<ApiList>> = TeletrackerApi.instance.getList(token, ctx.query.id)

    let itemsResponseFut: Promise<TeletrackerResponse<
      ApiItem[]
    >> = TeletrackerApi.instance.getListItems(
      token,
      ctx.query.id,
      {
        sort: filterParams.sortOrder,
        itemTypes: filterParams.itemTypes,
        genres: filterParams.genresFilter,
        networks: filterParams.networks,
        limit: 6,
      }
    );

    // Load page metadata (needed for filter calculation)
    let metadataFut = TeletrackerApi.instance.getMetadata();

    let [listResponse, itemsResponse, metadataResponse] = await Promise.all([
      listResponseFut,
      itemsResponseFut,
      metadataFut,
    ]);

    if (listResponse.ok && itemsResponse.ok && metadataResponse.ok) {
      let people = (listResponse.data!.data.relevantPeople || []).map(
        PersonFactory.create,
      );
      let list = ListFactory.create(listResponse.data!.data);

      const networks = metadataResponse.data!.data.networks;
      let defaultFilters: FilterParams = smartListRulesToFilters(
        list,
        networks,
      );

      await Promise.all([
        ctx.store.dispatch(
          ListRetrieveSuccess({
            list: list,
          }),
        ),
        ctx.store.dispatch(retrieveListItemsSucceeded({
          listId: list.id,
          items: itemsResponse.data!.data.map(ItemFactory.create),
          append: false,
          paging: itemsResponse.data!.paging,
          forFilters: list.isDynamic
            ? { ...defaultFilters, ...filterParams }
            : filterParams,
        })),
        ctx.store.dispatch(peopleFetchSuccess(people)),
        ctx.store.dispatch(
          loadMetadataSuccess({
            genres: metadataResponse.data!.data.genres,
            networks: metadataResponse.data!.data.networks,
          }),
        ),
      ]);

      return {
        preloaded: true,
      };
    } else {
      return {
        listStatus: listResponse.status,
        itemsStatus: itemsResponse.status,
      };
    }
  } else {
    return {
      preloaded: false,
    };
  }
};

export default ListDetailWrapper;
