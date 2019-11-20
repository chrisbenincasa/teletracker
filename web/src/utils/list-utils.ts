import {
  ListPersonRule,
  ListTagRule,
  List,
  ListConfiguration,
  ListGenreRule,
  ListItemTypeRule,
  ListRule,
  ListNetworkRule,
} from '../types';
import * as R from 'ramda';
import { GRID_COLUMNS, TOTAL_COLUMNS } from '../constants/';

export function isPersonRule(x: ListRule): x is ListPersonRule {
  return x.type === 'UserListPersonRule';
}

export function isTagRule(x: ListRule): x is ListTagRule {
  return x.type === 'UserListTagRule';
}

export function isGenreRule(x: ListRule): x is ListGenreRule {
  return x.type === 'UserListGenreRule';
}

export function isNetworkRule(x: ListRule): x is ListNetworkRule {
  return x.type === 'UserListNetworkRule';
}

export function isItemTypeRule(x: ListRule): x is ListItemTypeRule {
  return x.type === 'UserListItemTypeRule';
}

export function listTracksPerson(list: List, personId: string): boolean {
  if (list.configuration && list.configuration.ruleConfiguration) {
    list.configuration.ruleConfiguration.rules.forEach(rule => {
      if (isPersonRule(rule) && rule.personId === personId) {
        return true;
      }
    });

    return false;
  } else {
    return false;
  }
}

export function getOrInitListConfiguration(list: List) {
  if (!list.configuration) {
    list.configuration = {};
  }

  return list.configuration!;
}

function getOrInitListOptionsForConfiguration(
  listConfiguration: ListConfiguration,
) {
  if (!listConfiguration.options) {
    listConfiguration.options = { removeWatchedItems: false };
  }

  return listConfiguration.options!;
}

export const getOrInitListOptions = R.pipe(
  getOrInitListConfiguration,
  getOrInitListOptionsForConfiguration,
);

export function calculateLimit(
  screenWidth: string,
  numRows?: number,
  offsetNum?: number,
) {
  const columns = TOTAL_COLUMNS / GRID_COLUMNS[screenWidth];
  const rows = numRows || 3;
  const offset = offsetNum || 0;

  return columns * rows + offset;
}

export function getNumColumns(screenWidth: string) {
  return TOTAL_COLUMNS / GRID_COLUMNS[screenWidth];
}
