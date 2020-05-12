import reducer, { State, makeState } from '../../src/reducers/item-detail';
import { USER_SELF_UPDATE_ITEM_TAGS_SUCCESS } from '../../src/actions/user';
import { ActionType } from '../../src/types';
import { Map } from 'immutable';

describe('item-detail reducer', () => {
  it('should add tags when item has no tags field', () => {
    const state: State = makeState({
      fetching: false,
      fetchingRecs: false,
      thingsById: Map({
        id1: {
          id: 'id1',
          original_title: 'title',
          slug: 'slug',
          title: 'title',
          canonicalId: 'id1',
          canonicalTitle: 'title',
          type: 'movie',
          relativeUrl: '/',
          canonicalUrl: '/',
          itemMarkedAsWatched: false,
        },
      }),
    });

    let nextState = reducer(state, {
      type: USER_SELF_UPDATE_ITEM_TAGS_SUCCESS,
      payload: { itemId: 'id1', action: ActionType.Watched },
    });

    expect(nextState.thingsById.get('id1')!.tags).toEqual([
      {
        tag: ActionType.Watched,
      },
    ]);
  });
});
