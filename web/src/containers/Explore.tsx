import {
  CircularProgress,
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  useTheme,
} from '@material-ui/core';
import _ from 'lodash';
import { useRouter } from 'next/router';
import qs from 'querystring';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { clearPopular, retrievePopular } from '../actions/popular';
import Featured from '../components/Featured';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ItemCard from '../components/ItemCard';
import ScrollToTop from '../components/Buttons/ScrollToTop';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { DEFAULT_POPULAR_LIMIT, DEFAULT_ROWS } from '../constants';
import { getVoteAverage, getVoteCount } from '../utils/textHelper';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForNextRouter,
} from '../utils/urlHelper';
import { useStateDeepEq } from '../hooks/useStateDeepEq';
import { useWithUserContext } from '../hooks/useWithUser';
import { useWidth } from '../hooks/useWidth';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { usePrevious } from '../hooks/usePrevious';
import { Breakpoint } from '@material-ui/core/styles/createBreakpoints';
import { makeStyles } from '@material-ui/core/styles';
import { useDebouncedCallback } from 'use-debounce';
import {
  useDispatchAction,
  useDispatchSideEffect,
} from '../hooks/useDispatchAction';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    listTitle: {
      display: 'flex',
      flexDirection: 'row',
      alignItems: 'center',
      marginBottom: theme.spacing(1),
    },
    loadingBar: {
      flexGrow: 1,
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    popularContainer: {
      padding: theme.spacing(0, 3),
      [theme.breakpoints.down('sm')]: {
        padding: theme.spacing(0, 1),
      },
      display: 'flex',
      flexDirection: 'column',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    fin: {
      fontStyle: 'italic',
      textAlign: 'center',
      margin: theme.spacing(6),
    },
    popularWrapper: {
      display: 'flex',
      flexGrow: 1,
      flexDirection: 'column',
    },
  }),
);

interface Props {
  defaultFilters?: FilterParams;
}

function Explore(props: Props) {
  //
  // State
  //
  const classes = useStyles();
  const userState = useWithUserContext();
  const theme = useTheme();
  const width = useWidth();

  let [featuredItemsIndex, setFeaturedItemsIndex] = useState<number[]>([]);
  let [featuredItems, setFeaturedItems] = useState<Item[]>([]);
  let [showFilter, setShowFilter] = useState(false);
  let [needsNewFeatured, setNeedsNewFeatured] = useState(false);
  let totalLoadedImages = useRef(0);
  let [showScrollToTop, setShowScrollToTop] = useState(false);

  const popularWrapper = useRef<HTMLDivElement | null>(null);
  const previousWidth = usePrevious<Breakpoint>(width);
  const router = useRouter();

  let filterParams = props.defaultFilters || DEFAULT_FILTER_PARAMS;
  let paramsFromQuery = parseFilterParamsFromQs(qs.stringify(router.query));

  filterParams = {
    ...filterParams,
    ...paramsFromQuery,
  };

  let [filters, setFilters] = useStateDeepEq(filterParams, filterParamsEqual);

  const previousRouterQuery = usePrevious(router.query);
  const thingsById = useStateSelector(state => state.itemDetail.thingsById);
  const [popular, previousPopular] = useStateSelectorWithPrevious(
    state => state.popular.popular,
    (l, r) => {
      let res = _.isEqual(l, r);
      if (!res) {
        console.log('not equal', l, r);
      }
      return res;
    },
  );
  const popularBookmark = useStateSelector(
    state => state.popular.popularBookmark,
  );
  const genres = useStateSelector(state => state.metadata.genres, _.isEqual);
  const networks = useStateSelector(
    state => state.metadata.networks,
    _.isEqual,
  );
  const retrievePopularFromServer = useDispatchAction(retrievePopular);
  const clearPopularState = useDispatchSideEffect(clearPopular);
  const [loading, wasLoading] = useStateSelectorWithPrevious(
    state => state.popular.loadingPopular,
  );

  //
  // Logic
  //

  const onScroll = useCallback(() => {
    const scrollTop = window.pageYOffset || 0;
    // to do: 100 is just a random number, we can play with this or make it dynamic
    if (scrollTop > 100 && !showScrollToTop) {
      setShowScrollToTop(true);
    } else if (scrollTop < 100 && showScrollToTop) {
      setShowScrollToTop(false);
    }
  }, []);

  const handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(filters, filterParams)) {
      setFilters(filterParams);
      setNeedsNewFeatured(true);
    }
  };

  const getNumberFeaturedItems = useCallback(() => {
    if (['xs', 'sm'].includes(width)) {
      return 1;
    } else {
      return 2;
    }
  }, [width]);

  const loadPopular = (
    passBookmark: boolean,
    firstRun?: boolean,
    compensate?: number,
  ) => {
    if (!loading) {
      let numberFeaturedItems: number = getNumberFeaturedItems();

      // Reset our counter for total loaded images as we're about to replace
      // the whole grid.
      if (firstRun) {
        totalLoadedImages.current = 0;
      }

      retrievePopularFromServer({
        bookmark: passBookmark ? popularBookmark : undefined,
        limit: compensate
          ? compensate
          : calculateLimit(
              width,
              DEFAULT_ROWS,
              firstRun ? numberFeaturedItems : 0,
            ),
        filters,
      });
    }
  };

  const getFeaturedItems = useCallback(
    (numberFeaturedItems: number) => {
      // We'll use the initialLoadSize to slice the array to ensure the featured items don't change as the popular array size increases
      const initialLoadSize = calculateLimit(
        width,
        DEFAULT_ROWS,
        numberFeaturedItems,
      );

      // We only want Featured items that have a background and poster image
      // and we want them sorted by average score && vote count
      return popular && popular.length > 2
        ? popular
            .slice(0, initialLoadSize)
            .filter(id => {
              const item = thingsById[id];
              const hasBackdropImage =
                item.backdropImage && item.backdropImage.id;
              const hasPosterImage = item.posterImage && item.posterImage.id;
              return hasBackdropImage && hasPosterImage;
            })
            .sort((a, b) => {
              // TODO: Replace with weighted average
              const itemA = thingsById[a];
              const itemB = thingsById[b];
              const voteAverageA = getVoteAverage(itemA);
              const voteAverageB = getVoteAverage(itemB);
              const voteCountA = getVoteCount(itemA);
              const voteCountB = getVoteCount(itemB);
              return voteAverageB - voteAverageA || voteCountB - voteCountA;
            })
            .slice(0, numberFeaturedItems)
        : [];
    },
    [popular, thingsById, width],
  );

  const updateFeaturedItems = useCallback(() => {
    let numberFeaturedItems = getNumberFeaturedItems();
    const featuredItems = getFeaturedItems(numberFeaturedItems);

    // Require that there be at least 2 full rows before displaying Featured items.
    const featuredRequiredItems = calculateLimit(width, 2, numberFeaturedItems);
    const itemsInRows = DEFAULT_POPULAR_LIMIT - numberFeaturedItems;
    const hangerItems = itemsInRows % DEFAULT_ROWS;
    const missingItems = hangerItems > 0 ? DEFAULT_ROWS - hangerItems : 0;

    // If we don't have enough content to fill featured items, don't show any
    if (
      featuredItems.length < numberFeaturedItems ||
      popular!.length < featuredRequiredItems
    ) {
      // Prevent re-setting state if it's already been reset
      if (featuredItemsIndex.length > 0) {
        setFeaturedItemsIndex([]);
        setFeaturedItems([]);
        setNeedsNewFeatured(false);
      }
    } else {
      const featuredIndexes = featuredItems.map(item =>
        popular!.findIndex(id => item === id),
      );

      let newFeaturedItems = featuredIndexes.map(
        index => thingsById[popular![index]],
      );

      setFeaturedItemsIndex(featuredIndexes);
      setFeaturedItems(newFeaturedItems);
      setNeedsNewFeatured(false);
    }

    // We only want to fetch missing items if this is the SSR load
    // and we need to fill out space at the bottom
    if (popular?.length === DEFAULT_POPULAR_LIMIT && missingItems > 0) {
      loadPopular(true, false, missingItems);
    }
  }, [popular, thingsById, width, theme, featuredItemsIndex]);

  const scrollToTop = useCallback(() => {
    window.scrollTo(0, 0);
    setShowScrollToTop(false);
  }, []);

  const toggleFilters = () => {
    setShowFilter(prev => !prev);
  };

  const mapGenre = (genre: number) => {
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  const [debouncedLoadMore] = useDebouncedCallback(() => {
    loadPopular(true, false);
  }, 250);

  const loadMoreResults = useCallback(() => {
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems =
      (popular && popular.length - featuredItemsIndex.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages.current;
    const shouldLoadMore = totalNonLoadedImages <= numColumns;

    if (!loading && shouldLoadMore) {
      debouncedLoadMore();
    }
    if (!showScrollToTop) {
      setShowScrollToTop(true);
    }
  }, []);

  const setVisibleItems = useCallback(() => {
    totalLoadedImages.current += 1;
  }, []);

  //
  // Hooks
  //

  useEffect(() => {
    if (!popular) {
      loadPopular(false, true);
    } else {
      setNeedsNewFeatured(true);
    }

    window.addEventListener('scroll', onScroll, false);

    return () => {
      clearPopularState();
      window.removeEventListener('scroll', onScroll);
    };
  }, []);

  useEffect(() => {
    console.log('router effect');
    let query = qs.stringify(router.query);
    let prevQuery = qs.stringify(previousRouterQuery);

    if (query !== prevQuery) {
      console.log('router effect change');
      handleFilterParamsChange(parseFilterParamsFromQs(query));
    }
  }, [router]);

  useEffect(() => {
    console.log('filters effect');
    updateUrlParamsForNextRouter(
      router,
      filters,
      undefined,
      props.defaultFilters,
    );
    loadPopular(false, true);
  }, [filters]);

  useEffect(() => {
    const isInitialFetch = popular && !previousPopular && !loading;
    // const isNewFetch = popular && wasLoading && !loading;
    const didScreenResize =
      popular &&
      (!previousWidth ||
        ['xs', 'sm'].includes(previousWidth) !== ['xs', 'sm'].includes(width));
    const didNavigate = !isInitialFetch && needsNewFeatured;

    if (isInitialFetch || didScreenResize || didNavigate) {
      updateFeaturedItems();
    }
  }, [popular, loading, width, needsNewFeatured]);

  //
  // Render functions
  //

  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

  const renderLoading = () => {
    return (
      <div className={classes.loadingBar}>
        <LinearProgress />
      </div>
    );
  };

  const renderPopular = () => {
    const { genresFilter, itemTypes } = filters;

    return popular ? (
      <div className={classes.popularContainer}>
        <div className={classes.listTitle}>
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(width) ? 'h6' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Popular ${
              genresFilter && genresFilter.length === 1
                ? mapGenre(genresFilter[0])
                : ''
            } ${
              itemTypes && itemTypes.length === 1
                ? itemTypes.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
          <ShowFiltersButton onClick={toggleFilters} />
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={handleFilterParamsChange}
            isListDynamic={false}
            filters={filters}
            initialState={props.defaultFilters}
            defaultSort={props.defaultFilters?.sortOrder}
            variant="default"
          />
        </div>
        <AllFilters
          genres={genres}
          open={showFilter}
          filters={filters}
          updateFilters={handleFilterParamsChange}
          sortOptions={['popularity', 'recent', 'rating|imdb', 'rating|tmdb']}
          networks={networks}
        />
        {popular.length > 0 ? (
          <InfiniteScroll
            pageStart={0}
            loadMore={loadMoreResults}
            hasMore={Boolean(popularBookmark)}
            useWindow
            threshold={300}
          >
            <Grid container spacing={2} ref={popularWrapper}>
              {popular.map((result, index) => {
                let thing = thingsById[result];
                if (thing && !featuredItemsIndex.includes(index)) {
                  return (
                    <ItemCard
                      key={result}
                      userSelf={userState.userSelf}
                      item={thing}
                      hasLoaded={setVisibleItems}
                    />
                  );
                } else {
                  return null;
                }
              })}
            </Grid>
            {loading && renderLoadingCircle()}
            {!Boolean(popularBookmark) && (
              <Typography className={classes.fin}>fin.</Typography>
            )}
          </InfiniteScroll>
        ) : (
          <Typography>Sorry, nothing matches your filter.</Typography>
        )}
      </div>
    ) : null;
  };

  return popular ? (
    <div className={classes.popularWrapper}>
      <Featured featuredItems={featuredItems} />
      {renderPopular()}
      {showScrollToTop && (
        <ScrollToTop
          onClick={scrollToTop}
          style={{
            position: 'fixed',
            bottom: 8,
            right: 8,
            backgroundColor: '#00838f',
          }}
        />
      )}
    </div>
  ) : (
    renderLoading()
  );
}

// DEV MODE ONLY
// Explore.whyDidYouRender = true;

export default React.memo(Explore, (prevProps, nextProps) => {
  return _.isEqual(prevProps.defaultFilters, nextProps.defaultFilters);
});
