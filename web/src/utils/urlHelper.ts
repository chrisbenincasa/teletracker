import { RouteComponentProps } from 'react-router';
import _ from 'lodash';
import {
  isItemType,
  isListSortOption,
  isNetworkType,
  ListSortOptions,
} from '../types';
import { FilterParams } from './searchFilters';

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
  updateMultipleUrlParams(props, [[param, value]]);
};

export const updateMultipleUrlParams = (
  props: RouteComponentProps<any>,
  keyValuePairs: [string, any | undefined][],
): void => {
  if (keyValuePairs.length === 0) {
    return;
  }

  const { location } = props;
  let params = new URLSearchParams(location.search);

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

  props.history.replace(`?${params}`);
};

export function parseFilterParamsFromQs(qs: string): FilterParams {
  let params = new URLSearchParams(qs);
  let sortParam = params.get('sort');
  let itemTypeParam = params.get('type');
  let networkParam = params.get('networks');
  let genresParam = params.get('genres');

  let ryMin = params.get('ry_min');
  let ryMax = params.get('ry_max');

  return {
    sortOrder:
      sortParam && isListSortOption(sortParam)
        ? (sortParam as ListSortOptions)
        : 'default',

    itemTypes: itemTypeParam
      ? decodeURIComponent(itemTypeParam)
          .split(',')
          .filter(isItemType)
      : undefined,

    networks: networkParam
      ? decodeURIComponent(networkParam)
          .split(',')
          .filter(isNetworkType)
      : undefined,

    genresFilter: genresParam
      ? decodeURIComponent(genresParam)
          .split(',')
          .map(item => {
            return parseInt(item, 10);
          })
      : undefined,

    sliders: {
      releaseYear: {
        min: ryMin ? _.parseInt(ryMin, 10) : undefined,
        max: ryMax ? _.parseInt(ryMax, 10) : undefined,
      },
    },
  };
}
