import ActiveFilters from '../Filters/ActiveFilters';
import AllFilters from '../Filters/AllFilters';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { CircularProgress, Grid, Typography } from '@material-ui/core';
import ItemCard from '../ItemCard';
import React, { useCallback, useContext, useEffect } from 'react';
import useStyles from './ListDetail.styles';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../../hooks/useStateSelector';
import selectList from '../../selectors/selectList';
import { useDebouncedCallback } from 'use-debounce';
import { LIST_RETRIEVE_INITIATED, getList } from '../../actions/lists';
import { useDispatchAction } from '../../hooks/useDispatchAction';
import { calculateLimit } from '../../utils/list-utils';
import { useWidth } from '../../hooks/useWidth';
import { FilterContext } from '../Filters/FilterContext';
import { List } from '../../types';
import { usePrevious } from '../../hooks/usePrevious';
import { filterParamsEqual } from '../../utils/changeDetection';
import {
  LIST_ITEMS_RETRIEVE_INITIATED,
  retrieveListItems,
} from '../../actions/lists/get_list_items';
import selectItems from '../../selectors/selectItems';

interface Props {
  readonly showFilter: boolean;
  readonly listId: string;
}

export default function ListItems(props: Props) {
  const classes = useStyles();
  const width = useWidth();

  const [list, previousList] = useStateSelectorWithPrevious(state =>
    selectList(state, props.listId),
  );
  const listLoading = useStateSelector(
    state => state.lists.loading[LIST_RETRIEVE_INITIATED],
  );
  const listItemsLoading = useStateSelector(
    state => state.lists.loading[LIST_ITEMS_RETRIEVE_INITIATED],
  );
  const listItems = useStateSelector(state => {
    if (state.lists?.current?.listId !== props.listId) {
      return [];
    } else {
      return selectItems(state, state.lists?.current?.items || []);
    }
  });
  const listBookmark = useStateSelector(
    state => state.lists?.current?.bookmark,
  );
  const totalItemsForFilters = useStateSelector(
    state => state.lists?.current?.total,
  );
  const hasMoreItems =
    _.isUndefined(totalItemsForFilters) ||
    listItems.length < totalItemsForFilters;

  const currentFilterState = useStateSelector(state => state.lists.current);
  const { filters, defaultFilters, clearFilters } = useContext(FilterContext);
  const previousListId = usePrevious(props.listId);

  const dispatchRetrieveList = useDispatchAction(retrieveListItems);

  const retrieveList = (initialLoad: boolean) => {
    dispatchRetrieveList({
      listId: props.listId,
      limit: calculateLimit(width, 3),
      bookmark: initialLoad ? undefined : listBookmark,
      filters: { ...filters },
    });
  };

  const [loadMoreDebounced] = useDebouncedCallback((passBookmark: boolean) => {
    if (!listItemsLoading) {
      dispatchRetrieveList({
        listId: props.listId,
        limit: calculateLimit(width, 3),
        bookmark: passBookmark ? listBookmark : undefined,
        filters: { ...filters },
      });
    }
  }, 200);

  const loadMoreList = useCallback(() => {
    // If previousListId is undefined, this is the first list we've loaded into
    // the component so far. As long as it stays undefined, we're on the same
    // list. Once the list id changes, we'll always compare the current id to
    // the recorded previous
    const sameList = !previousListId || previousListId === props.listId;

    if (listBookmark && !listItemsLoading && sameList) {
      // Otherwise, keep loading more items on the same list using the bookmark
      loadMoreDebounced(true);
    }
  }, [listBookmark, listItemsLoading, props.listId]);

  useEffect(() => {
    const sameList = !previousListId || previousListId === props.listId;
    let currentFilters = currentFilterState?.filters;
    if (
      sameList &&
      !filterParamsEqual(filters, currentFilters, defaultFilters?.sortOrder)
    ) {
      retrieveList(true);
    }
  }, [filters, props.listId, currentFilterState]);

  useEffect(() => {
    if (_.isUndefined(previousList) && !_.isUndefined(list)) {
      retrieveList(true);
    }
  }, [list, previousList]);

  const renderNoContentMessage = (list: List) => {
    let notLoading = !listLoading && !listItemsLoading;
    if (list.totalItems > 0 && notLoading) {
      return 'Sorry, nothing matches your current filter.  Please update and try again.';
    } else if (list.totalItems === 0 && notLoading && !list.isDynamic) {
      return 'Nothing has been added to this list yet.';
    } else if (list.totalItems === 0 && notLoading && list.isDynamic) {
      return 'Nothing currently matches this Smart Lists filtering criteria';
    }
  };

  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

  return !list ? null : (
    <React.Fragment>
      <div className={classes.filters}>
        <ActiveFilters isListDynamic={list.isDynamic} variant="default" />
      </div>
      <AllFilters open={props.showFilter} isListDynamic={list.isDynamic} />
      <InfiniteScroll
        pageStart={0}
        loadMore={loadMoreList}
        hasMore={!_.isUndefined(listBookmark) && hasMoreItems}
        useWindow
        threshold={300}
      >
        <Grid container spacing={2}>
          {listItems.length > 0 ? (
            listItems.map(item => {
              return (
                <ItemCard
                  key={item.id}
                  itemId={item.id}
                  listContext={list}
                  withActionButton
                  showDelete={!list.isDynamic && list.ownedByRequester}
                />
              );
            })
          ) : (
            <Typography
              align="center"
              color="secondary"
              display="block"
              className={classes.noContent}
            >
              {renderNoContentMessage(list)}
            </Typography>
          )}
        </Grid>
        {listItemsLoading && renderLoadingCircle()}
      </InfiniteScroll>
    </React.Fragment>
  );
}
