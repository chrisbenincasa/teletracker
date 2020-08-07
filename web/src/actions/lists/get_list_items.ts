import { FilterParams, normalizeFilterParams } from '../../utils/searchFilters';
import { FSA } from 'flux-standard-action';
import { Paging } from '../../types';
import { createAction } from '../utils';
import { Item, ItemFactory } from '../../types/v2/Item';
import { put, takeEvery } from '@redux-saga/core/effects';
import { TeletrackerResponse } from '../../utils/api-client';
import { clientEffect } from '../clientEffect';
import _ from 'lodash';
import { ApiItem } from '../../types/v2';

export const LIST_ITEMS_RETRIEVE_INITIATED = 'lists/items/retrieve/INITIATED';
export const LIST_ITEMS_RETRIEVE_SUCCESS = 'lists/items/retrieve/SUCCESS';
export const LIST_ITEMS__RETRIEVE_FAILED = 'lists/items/retrieve/FAILED';

export interface ListItemsRetrieveInitiatedPayload {
  listId: string;
  desc?: boolean;
  filters?: FilterParams;
  bookmark?: string;
  limit?: number;
}

export type ListItemsRetrieveInitiatedAction = FSA<
  typeof LIST_ITEMS_RETRIEVE_INITIATED,
  ListItemsRetrieveInitiatedPayload
>;

export type ListItemsRetrieveSuccessPayload = {
  readonly listId: string;
  readonly items: ReadonlyArray<Item>;
  readonly paging?: Paging;
  readonly append: boolean;
  readonly forFilters?: FilterParams;
};

export type ListItemsRetrieveSuccessAction = FSA<
  typeof LIST_ITEMS_RETRIEVE_SUCCESS,
  ListItemsRetrieveSuccessPayload
>;

export type ListItemsRetrieveFailedAction = FSA<
  typeof LIST_ITEMS__RETRIEVE_FAILED,
  Error
>;

export const retrieveListItems = createAction<ListItemsRetrieveInitiatedAction>(
  LIST_ITEMS_RETRIEVE_INITIATED,
);

export const retrieveListItemsSucceeded = createAction<
  ListItemsRetrieveSuccessAction
>(LIST_ITEMS_RETRIEVE_SUCCESS);

export const retrieveListItemsFailed = createAction<
  ListItemsRetrieveFailedAction
>(LIST_ITEMS__RETRIEVE_FAILED);

export const retrieveListItemsSaga = function*() {
  yield takeEvery(LIST_ITEMS_RETRIEVE_INITIATED, function*({
    payload,
  }: ListItemsRetrieveInitiatedAction) {
    if (payload) {
      try {
        // TODO: Type alias to make this cleaner
        let response: TeletrackerResponse<ApiItem[]> = yield clientEffect(
          client => client.getListItems,
          payload.listId,
          {
            itemTypes: payload.filters?.itemTypes,
            networks: payload.filters?.networks,
            bookmark: payload.bookmark,
            sort: payload.filters?.sortOrder,
            limit: payload.limit,
            genres: payload.filters?.genresFilter,
            releaseYearRange: payload.filters?.sliders?.releaseYear,
            castIncludes: payload.filters?.people,
            imdbRating: payload.filters?.sliders?.imdbRating,
            offerTypes: payload.filters?.offers?.types,
          },
        );

        const filters: FilterParams = normalizeFilterParams(
          payload.filters || {},
        );

        if (response.ok && response.data) {
          const items = response.data!.data.map(ItemFactory.create);

          yield put(
            retrieveListItemsSucceeded({
              listId: payload.listId,
              items,
              paging: response.data.paging,
              append: !_.isUndefined(payload.bookmark),
              forFilters: filters,
            }),
          );
        } else {
          yield put(retrieveListItemsFailed(new Error('bad response')));
        }
      } catch (e) {
        yield put(retrieveListItemsFailed(e));
      }
    } else {
      yield put(retrieveListItemsFailed(new Error('No payload defined.')));
    }
  });
};
