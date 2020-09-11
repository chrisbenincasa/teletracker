import _ from 'lodash';
import {
  isItemType,
  isListSortOption,
  isNetworkType,
  isNetworkTypeOrAll,
  isOfferType,
  SortOptions,
} from '../types';
import {
  FilterParams,
  removeUndefinedKeys,
  SelectableNetworks,
} from './searchFilters';
import querystring from 'querystring';
import url from 'url';
import { filterParamsEqual } from './changeDetection';
import { NextRouter } from 'next/router';
import produce from 'immer';

const validQueryParams = [
  'genres',
  'networks',
  'sort',
  'type',
  'ry_min',
  'ry_max',
  'cast',
  'imdbRating',
  'q',
];

export const updateUrlParamsForNextRouter = (
  router: NextRouter,
  filterParams: FilterParams,
  excludedParams?: string[],
  defaultFilters?: FilterParams,
): void => {
  let sanitizedPath = url.parse(router.asPath).pathname;

  let sanitizedFilterQuery = _.pickBy(router.query, (value, key) =>
    _.includes(validQueryParams, key),
  );

  if (
    defaultFilters &&
    filterParamsEqual(defaultFilters, filterParams, defaultFilters?.sortOrder)
  ) {
    router.push(router.pathname, sanitizedPath, { shallow: true });
    return;
  }

  let imdbMin = filterParams.sliders?.imdbRating?.min || '';
  let imdbMax = filterParams.sliders?.imdbRating?.max || '';
  let imdbPart: any | undefined;
  if (imdbMin || imdbMax) {
    imdbPart = `${imdbMin}:${imdbMax}`;
  }

  const sortOrderForUrl =
    defaultFilters?.sortOrder === filterParams.sortOrder
      ? undefined
      : filterParams.sortOrder;

  let paramUpdates: [string, any | undefined][] = [
    ['genres', filterParams.genresFilter],
    ['networks', filterParams.networks],
    ['sort', sortOrderForUrl],
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
    ['imdbRating', imdbPart],
    ['ot', filterParams.offers?.types],
  ];

  paramUpdates = paramUpdates.filter(
    ([key, _]) => !excludedParams || !excludedParams.includes(key),
  );

  updateMultipleUrlParams(
    querystring.stringify(sanitizedFilterQuery),
    str => {
      router.push(router.pathname + str, sanitizedPath + str, {
        shallow: true,
      });
    },
    paramUpdates,
  );
};

const updateMultipleUrlParams = (
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

  if (Array.from(params.entries()).length === 0) {
    replace('');
  } else {
    replace(`?${params}`);
  }
};

export function parseFilterParams(params: Map<string, string>): FilterParams {
  let sortParam = params.get('sort');
  let itemTypeParam = params.get('type');
  let networkParam = params.get('networks');
  let genresParam = params.get('genres');
  let peopleParam = params.get('cast');

  let ryMin = params.get('ry_min');
  let ryMax = params.get('ry_max');

  let imdbRating = params.get('imdbRating');

  let offerTypes = params.get('ot');

  let filters: FilterParams = {
    sortOrder:
      sortParam && isListSortOption(sortParam)
        ? (sortParam as SortOptions)
        : undefined,
  };

  let itemTypes = itemTypeParam
    ? decodeURIComponent(itemTypeParam)
        .split(',')
        .filter(isItemType)
    : undefined;

  let rawNetworks = networkParam
    ? decodeURIComponent(networkParam).split(',')
    : [];
  let networkSetting: SelectableNetworks;
  if (rawNetworks.length === 1 && rawNetworks[0] === 'all') {
    networkSetting = 'all';
  } else if (rawNetworks.length > 0) {
    // Not valid to have 'all' + other networks
    networkSetting = rawNetworks.filter(n => n !== 'all').filter(isNetworkType);
  }

  let offerTypesValue = offerTypes
    ? decodeURIComponent(offerTypes)
        .split(',')
        .filter(isOfferType)
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

  filters = produce(filters, draft => {
    if (itemTypes) {
      draft.itemTypes = itemTypes;
    }

    if (networkSetting) {
      draft.networks = networkSetting;
    }

    if (genres) {
      draft.genresFilter = genres;
    }

    if (people) {
      draft.people = people;
    }

    let releaseYearMin = ryMin ? _.parseInt(ryMin, 10) : undefined;
    let releaseYearMax = ryMax ? _.parseInt(ryMax, 10) : undefined;

    if (releaseYearMin || releaseYearMax) {
      draft.sliders = {
        ...(draft.sliders || {}),
        releaseYear: {
          min: releaseYearMin,
          max: releaseYearMax,
        },
      };
    }

    let [imdbMin, imdbMax, ...rest] = imdbRating
      ? decodeURIComponent(imdbRating).split(':', 2)
      : [undefined, undefined];
    let imdbMinNum = imdbMin ? parseFloat(imdbMin) : undefined;
    let imdbMaxNum = imdbMax ? parseFloat(imdbMax) : undefined;

    if (
      (imdbMinNum && !_.isNaN(imdbMinNum)) ||
      (imdbMaxNum && !_.isNaN(imdbMaxNum))
    ) {
      draft.sliders = {
        ...filters.sliders,
        imdbRating: {
          min: imdbMinNum,
          max: imdbMaxNum,
        },
      };
    }

    if (offerTypesValue) {
      draft.offers = {
        ...filters.offers,
        types: offerTypesValue,
      };
    }
  });

  return removeUndefinedKeys(filters);
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
