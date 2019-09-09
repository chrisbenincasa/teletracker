import { PopularInitiatedAction, PopularSuccessfulAction } from './popular';

export * from './popular';
export * from './genre';

export type PopularActionTypes =
  | PopularInitiatedAction
  | PopularSuccessfulAction;
