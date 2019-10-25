import { PopularInitiatedAction, PopularSuccessfulAction } from './popular';

export * from './popular';

export type PopularActionTypes =
  | PopularInitiatedAction
  | PopularSuccessfulAction;
