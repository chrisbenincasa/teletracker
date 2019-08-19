import { FSA } from 'flux-standard-action';
import { createAction, createBasicAction } from '../utils';

export const SET_TOKEN = 'auth/SET_TOKEN';
export const UNSET_TOKEN = 'auth/UNSET_TOKEN';
export const TOKEN_SET = 'auth/TOKEN_SET';

export type SetTokenAction = FSA<typeof SET_TOKEN, string>;
export type UnsetTokenAction = FSA<typeof UNSET_TOKEN>;

export const SetToken = createAction<SetTokenAction>(SET_TOKEN);
export const UnsetToken = createBasicAction<UnsetTokenAction>(UNSET_TOKEN);
