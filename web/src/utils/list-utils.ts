import {
  ListRuleTypes,
  ListPersonRule,
  ListTagRule,
  List,
  ListConfiguration,
} from '../types';
import * as R from 'ramda';
import { GRID_COLUMNS, TOTAL_COLUMNS } from '../constants/';

export function isPersonRule(x: ListRuleTypes): x is ListPersonRule {
  return x.type === 'TrackedListPersonRule';
}

export function isTagRule(x: ListRuleTypes): x is ListTagRule {
  return x.type === 'TrackedListTagRule';
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
