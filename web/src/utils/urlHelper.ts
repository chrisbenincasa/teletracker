import { RouteComponentProps } from 'react-router';
import _ from 'lodash';
import {
  isItemType,
  isListSortOption,
  isNetworkType,
  SortOptions,
} from '../types';
import { FilterParams } from './searchFilters';
import { WithRouterProps } from 'next/dist/client/with-router';
import querystring from 'querystring';
import url from 'url';

/**
 * Updates or adds URL parameters
 * @param props
 * @param param
 * @param value
 */
export const updateURLParameters = (
  props: RouteComponentProps<any>,
  param: string,
  value?: any,
) => {
  updateMultipleUrlParams(
    props.location.search,
    str => props.history.replace(str),
    [[param, value]],
  );
};

export const updateUrlParamsForFilterRouter = (
  props: WithRouterProps,
  filterParams: FilterParams,
  excludedParams?: string[],
): void => {
  let paramUpdates: [string, any | undefined][] = [
    ['genres', filterParams.genresFilter],
    ['networks', filterParams.networks],
    [
      'sort',
      filterParams.sortOrder === 'default' ? undefined : filterParams.sortOrder,
    ],
    ['type', filterParams.itemTypes],
    [
      'ry_min',
      filterParams.sliders && filterParams.sliders.releaseYear
        ? filterParams.sliders.releaseYear.min
        : undefined,
    ],
    [
      'ry_max',
      filterParams.sliders && filterParams.sliders.releaseYear
        ? filterParams.sliders.releaseYear.max
        : undefined,
    ],
    ['cast', filterParams.people],
  ];

  paramUpdates = paramUpdates.filter(
    ([key, _]) => !excludedParams || !excludedParams.includes(key),
  );

  let sanitizedPath = url.parse(props.router.asPath).pathname;

  updateMultipleUrlParams(
    querystring.stringify(props.router.query),
    str =>
      props.router.replace(sanitizedPath + str, undefined, { shallow: true }),
    paramUpdates,
  );
};

export const updateUrlParamsForFilter = (
  props: RouteComponentProps<any>,
  filterParams: FilterParams,
  excludedParams?: string[],
): void => {
  let paramUpdates: [string, any | undefined][] = [
    ['genres', filterParams.genresFilter],
    ['networks', filterParams.networks],
    [
      'sort',
      filterParams.sortOrder === 'default' ? undefined : filterParams.sortOrder,
    ],
    ['type', filterParams.itemTypes],
    [
      'ry_min',
      filterParams.sliders && filterParams.sliders.releaseYear
        ? filterParams.sliders.releaseYear.min
        : undefined,
    ],
    [
      'ry_max',
      filterParams.sliders && filterParams.sliders.releaseYear
        ? filterParams.sliders.releaseYear.max
        : undefined,
    ],
    ['cast', filterParams.people],
  ];

  paramUpdates = paramUpdates.filter(
    ([key, _]) => !excludedParams || !excludedParams.includes(key),
  );

  updateMultipleUrlParams(
    props.location.search,
    str => props.history.replace(str),
    paramUpdates,
  );
};

export const updateMultipleUrlParams = (
  qs: string,
  replace: (str: string) => void,
  keyValuePairs: [string, any | undefined][],
): void => {
  if (keyValuePairs.length === 0) {
    return;
  }

  let params = new URLSearchParams(qs);

  keyValuePairs.forEach(([param, value]) => {
    let paramExists = params.get(param);

    let cleanValues = _.isArray(value) ? value.join(',') : value;

    // User is clicking button more than once, exit
    if (paramExists && value && paramExists === cleanValues) {
      return;
    }

    // TODO: Deletes when value is 0, is that OK?
    if (!cleanValues) {
      params.delete(param);
    } else if (paramExists) {
      params.set(param, cleanValues);
    } else {
      params.append(param, cleanValues);
    }
  });

  params.sort();

  replace(`?${params}`);
};

export function parseFilterParams(params: Map<string, string>) {
  let sortParam = params.get('sort');
  let itemTypeParam = params.get('type');
  let networkParam = params.get('networks');
  let genresParam = params.get('genres');
  let peopleParam = params.get('cast');

  let ryMin = params.get('ry_min');
  let ryMax = params.get('ry_max');

  let filters: FilterParams = {
    sortOrder:
      sortParam && isListSortOption(sortParam)
        ? (sortParam as SortOptions)
        : 'default',
  };

  let itemTypes = itemTypeParam
    ? decodeURIComponent(itemTypeParam)
        .split(',')
        .filter(isItemType)
    : undefined;

  let networks = networkParam
    ? decodeURIComponent(networkParam)
        .split(',')
        .filter(isNetworkType)
    : undefined;

  let genres = genresParam
    ? decodeURIComponent(genresParam)
        .split(',')
        .map(item => {
          return parseInt(item, 10);
        })
    : undefined;

  let people = peopleParam
    ? decodeURIComponent(peopleParam)
        .split(',')
        .map(item => item.trim())
        .filter(item => item.length > 0)
    : undefined;

  if (itemTypes) {
    filters.itemTypes = itemTypes;
  }

  if (networks) {
    filters.networks = networks;
  }

  if (genres) {
    filters.genresFilter = genres;
  }

  if (people) {
    filters.people = people;
  }

  let releaseYearMin = ryMin ? _.parseInt(ryMin, 10) : undefined;
  let releaseYearMax = ryMax ? _.parseInt(ryMax, 10) : undefined;

  if (releaseYearMin || releaseYearMax) {
    filters.sliders = {
      ...(filters.sliders || {}),
      releaseYear: {
        min: releaseYearMin,
        max: releaseYearMax,
      },
    };
  }

  return filters;
}

export function parseFilterParamsFromObject(obj: {
  [key: string]: string | string[];
}) {
  let map = new Map<string, string>();
  for (let key in obj) {
    const value = obj[key];
    if (_.isArray(value) && value.length > 0) {
      map.set(key, value[0]);
    } else if (_.isString(value)) {
      map.set(key, value);
    }
  }

  return parseFilterParams(map);
}

export function parseFilterParamsFromQs(qs: string): FilterParams {
  let params = new URLSearchParams(qs);
  let map = new Map<string, string>();
  params.forEach((value, key) => {
    map.set(key, value);
  });
  return parseFilterParams(map);
}
