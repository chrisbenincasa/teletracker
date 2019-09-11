import { ListRuleTypes, ListPersonRule, ListTagRule, List } from '../types';

export function isPersonRule(x: ListRuleTypes): x is ListPersonRule {
  return x.type === 'TrackedListPersonRule';
}

export function isTagRule(x: ListRuleTypes): x is ListTagRule {
  return x.type === 'TrackedListTagRule';
}

export function listTracksPerson(list: List, personId: string): boolean {
  if (list.configuration) {
    list.configuration.rules.forEach(rule => {
      if (isPersonRule(rule) && rule.personId === personId) {
        return true;
      }
    });

    return false;
  } else {
    return false;
  }
}
