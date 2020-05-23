import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import {
  ApiList,
  List,
  ListFactory,
  ListGenreRule,
  ListNetworkRule,
  ListPersonRule,
  ruleIsType,
} from '../../types';
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
import {
  DEFAULT_FILTER_PARAMS,
  FilterParams,
  normalizeFilterParams,
} from '../../utils/searchFilters';
import { collect } from '../../utils/collection-utils';
import _ from 'lodash';
import produce from 'immer';
import { PersonFactory } from '../../types/v2/Person';
import { peopleFetchSuccess } from '../../actions/people/get_people';

interface Props {
  readonly list?: List;
  readonly pageProps: any;
  readonly error?: number;
  readonly defaultFilters: FilterParams;
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
        <WithItemFilters defaultFilters={props.defaultFilters}>
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
      let people = (response.data!.data.relevantPeople || []).map(
        PersonFactory.create,
      );
      let list = ListFactory.create(response.data!.data);

      let defaultFilters: FilterParams = {};
      if (list.isDynamic) {
        let ruleConfiguration = list.configuration?.ruleConfiguration;
        let sort = ruleConfiguration?.sort;
        let rules = ruleConfiguration?.rules || [];

        defaultFilters = normalizeFilterParams(
          produce(defaultFilters, draft => {
            draft.genresFilter = collect(rules, rule =>
              ruleIsType<ListGenreRule>(rule, 'UserListGenreRule')
                ? rule
                : undefined,
            ).map(rule => rule.genreId);

            // draft.networks = collect(rules, rule =>
            //   ruleIsType<ListNetworkRule>(rule, 'UserListNetworkRule') ? rule : undefined,
            // ).map(rule => rule.networkId);

            draft.people = collect(rules, rule =>
              ruleIsType<ListPersonRule>(rule, 'UserListPersonRule')
                ? rule
                : undefined,
            ).map(rule => rule.personId);
          }),
        );
      }

      await Promise.all([
        ctx.store.dispatch(
          ListRetrieveSuccess({
            list: list,
            paging: response.data!.paging,
            append: false,
            forFilters: list.isDynamic
              ? { ...defaultFilters, ...filterParams }
              : filterParams,
          }),
        ),
        ctx.store.dispatch(peopleFetchSuccess(people)),
      ]);

      return {
        list,
        defaultFilters,
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
