import { FSA } from 'flux-standard-action';
import { createAction } from '@reduxjs/toolkit';
import { withPayloadType } from '../utils';

export const SET_TOKEN = 'auth/SET_TOKEN';
export const UNSET_TOKEN = 'auth/UNSET_TOKEN';
export const TOKEN_SET = 'auth/TOKEN_SET';

export type SetTokenAction = FSA<typeof SET_TOKEN, string>;
export type UnsetTokenAction = FSA<typeof UNSET_TOKEN>;

export const setToken = createAction(SET_TOKEN, withPayloadType<string>());
export const unsetToken = createAction(UNSET_TOKEN);
