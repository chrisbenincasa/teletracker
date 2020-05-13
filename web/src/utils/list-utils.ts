import {
  List,
  ListGenreRule,
  ListItemTypeRule,
  ListNetworkRule,
  ListPersonRule,
  ListRule,
  ListTagRule,
} from '../types';
import { GRID_ITEM_SIZE_IN_COLUMNS, TOTAL_COLUMNS } from '../constants/';

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

export function calculateLimit(
  screenWidth: string,
  numRows?: number,
  offsetNum?: number,
) {
  const columns = TOTAL_COLUMNS / GRID_ITEM_SIZE_IN_COLUMNS[screenWidth];
  const rows = numRows || 3;
  const offset = offsetNum || 0;

  return columns * rows + offset;
}

export function getNumColumns(screenWidth: string) {
  return TOTAL_COLUMNS / GRID_ITEM_SIZE_IN_COLUMNS[screenWidth];
}
