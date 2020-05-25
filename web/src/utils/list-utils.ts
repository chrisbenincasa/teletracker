import {
  List,
  ListGenreRule,
  ListItemTypeRule,
  ListNetworkRule,
  ListPersonRule,
  ListReleaseYearRule,
  ListRule,
  ListTagRule,
  Network,
  OpenRange,
  ruleIsType,
} from '../types';
import { GRID_ITEM_SIZE_IN_COLUMNS, TOTAL_COLUMNS } from '../constants/';
import { FilterParams, normalizeFilterParams } from './searchFilters';
import produce from 'immer';
import { collect, collectFirst } from './collection-utils';

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

export function isReleaseYearRule(x: ListRule): x is ListReleaseYearRule {
  return x.type === 'UserListReleaseYearRule';
}

export function smartListRulesToFilters(
  list: List,
  networks: ReadonlyArray<Network>,
): FilterParams {
  let defaultFilters: FilterParams = {};
  if (list.isDynamic) {
    let ruleConfiguration = list.configuration?.ruleConfiguration;
    let sort = ruleConfiguration?.sort;
    let rules = ruleConfiguration?.rules || [];

    return normalizeFilterParams(
      produce(defaultFilters, draft => {
        draft.sortOrder = sort?.sort;

        // TODO: Expose tag rules as filters

        draft.genresFilter = collect(rules, rule =>
          isGenreRule(rule) ? rule.genreId : undefined,
        );

        draft.itemTypes = collect(rules, rule =>
          isItemTypeRule(rule) ? rule.itemType : undefined,
        );

        draft.networks = collect(rules, rule =>
          isNetworkRule(rule)
            ? collectFirst(networks, n =>
                n.id === rule.networkId ? n.slug : undefined,
              )
            : undefined,
        );

        let releaseYearFilter = collectFirst(rules, rule =>
          isReleaseYearRule(rule)
            ? ({ min: rule.minimum, max: rule.maximum } as OpenRange)
            : undefined,
        );

        if (!draft.sliders) {
          draft.sliders = {};
        }

        draft.sliders!.releaseYear = releaseYearFilter;

        draft.people = collect(rules, rule =>
          isPersonRule(rule) ? rule.personId : undefined,
        );
      }),
    );
  } else {
    return defaultFilters;
  }
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
