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
import React, {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { clearPopular, retrievePopular } from '../actions/popular';
import Featured from '../components/Featured';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ItemCard from '../components/ItemCard';
import ScrollToTopContainer from '../components/ScrollToTopContainer';
import { calculateLimit } from '../utils/list-utils';
import {
  DEFAULT_POPULAR_LIMIT,
  DEFAULT_ROWS,
  itemsPerRow,
  TOTAL_COLUMNS,
} from '../constants';
import { getVoteAverage, getVoteCount } from '../utils/textHelper';
import { useStateDeepEq } from '../hooks/useStateDeepEq';
import { useWidth } from '../hooks/useWidth';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { usePrevious, usePreviousDeepEq } from '../hooks/usePrevious';
import { Breakpoint } from '@material-ui/core/styles/createBreakpoints';
import { makeStyles } from '@material-ui/core/styles';
import { useDebouncedCallback } from 'use-debounce';
import {
  useDispatchAction,
  useDispatchSideEffect,
} from '../hooks/useDispatchAction';
import { useGenres } from '../hooks/useStateMetadata';
import { Id } from '../types/v2';
import { FilterContext } from '../components/Filters/FilterContext';
import selectItems from '../selectors/selectItems';
import useFilterLoadEffect from '../hooks/useFilterLoadEffect';
import useIsMobile from '../hooks/useIsMobile';

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

function Explore() {
  //
  // State
  //
  const classes = useStyles();
  const theme = useTheme();
  const width = useWidth();
  const isMobile = useIsMobile();

  let [featuredItemsIndex, setFeaturedItemsIndex] = useStateDeepEq<number[]>(
    [],
  );
  let [featuredItems, setFeaturedItems] = useStateDeepEq<Id[]>([]);
  let [showFilter, setShowFilter] = useState(false);
  let [needsNewFeatured, setNeedsNewFeatured] = useState(false);

  const popularWrapper = useRef<HTMLDivElement | null>(null);
  const previousWidth = usePrevious<Breakpoint>(width);

  let { filters } = useContext(FilterContext);

  const thingsById = useStateSelector(state => state.itemDetail.thingsById);
  const popular = useStateSelector(state =>
    selectItems(state, state.popular.popular || []),
  );
  const previousPopular = usePreviousDeepEq(popular);
  const popularBookmark = useStateSelector(
    state => state.popular.popularBookmark,
  );
  const genres = useGenres();
  const retrievePopularFromServer = useDispatchAction(retrievePopular);
  const clearPopularState = useDispatchSideEffect(clearPopular);
  const [loading, wasLoading] = useStateSelectorWithPrevious(
    state => state.popular.loadingPopular,
  );
  const isFirstLoad = useStateSelector(state => state.popular.isFirstLoad);

  //
  // Logic
  //

  const getNumberFeaturedItems = useCallback(() => {
    if (isMobile) {
      return 1;
    } else {
      return 2;
    }
  }, [isMobile]);

  const loadPopular = (passBookmark: boolean, compensate?: number) => {
    if (!loading) {
      let numberFeaturedItems: number = getNumberFeaturedItems();

      retrievePopularFromServer({
        bookmark: passBookmark ? popularBookmark : undefined,
        limit: calculateLimit(width, DEFAULT_ROWS) + (compensate || 0),
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
            .filter(item => {
              const hasBackdropImage =
                item.backdropImage && item.backdropImage.id;
              const hasPosterImage = item.posterImage && item.posterImage.id;
              return hasBackdropImage && hasPosterImage;
            })
            .sort((itemA, itemB) => {
              // TODO: Replace with weighted average
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
    const itemsInRows = DEFAULT_POPULAR_LIMIT; // - numberFeaturedItems;
    const itemsPerRowForWidth = itemsPerRow(width) || TOTAL_COLUMNS;
    const hangerItems = itemsInRows % itemsPerRowForWidth;
    const missingItems =
      hangerItems > 0 ? itemsPerRowForWidth - hangerItems : 0;

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

      let newFeaturedItems = featuredIndexes.map(index => popular![index].id);

      setFeaturedItemsIndex(featuredIndexes);
      setFeaturedItems(newFeaturedItems);
      setNeedsNewFeatured(false);
    }

    // We only want to fetch missing items if this is the SSR load
    // and we need to fill out space at the bottom
    if (popular?.length === DEFAULT_POPULAR_LIMIT && missingItems > 0) {
      loadPopular(true, missingItems);
    }
  }, [popular, thingsById, width, theme, featuredItemsIndex]);

  const toggleFilters = () => {
    setShowFilter(prev => !prev);
  };

  const mapGenre = (genre: number) => {
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  const [debouncedLoadMore] = useDebouncedCallback(() => {
    loadPopular(true);
  }, 250);

  const loadMoreResults = useCallback(() => {
    if (!loading) {
      debouncedLoadMore();
    }
  }, [popular]);

  //
  // Hooks
  //

  useEffect(() => {
    if (!popular) {
      loadPopular(false);
    } else {
      setNeedsNewFeatured(true);
    }

    return () => {
      clearPopularState();
    };
  }, []);

  useFilterLoadEffect(
    () => {
      loadPopular(false);
    },
    state => state.popular.currentFilters,
  );

  useEffect(() => {
    let numberFeaturedItems = getNumberFeaturedItems();
    const isInitialFetch = popular && !previousPopular && !loading;
    const isNewFetch =
      popular &&
      wasLoading &&
      !loading &&
      popular?.length - numberFeaturedItems <= DEFAULT_POPULAR_LIMIT;
    const didScreenResize =
      popular &&
      !_.isUndefined(previousWidth) &&
      ['xs', 'sm'].includes(previousWidth) !== ['xs', 'sm'].includes(width);
    const didNavigate = !isInitialFetch && needsNewFeatured;

    if (isInitialFetch || isNewFetch || didScreenResize || didNavigate) {
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

    return popular && popular.length > 0 ? (
      <div className={classes.popularContainer}>
        <div className={classes.listTitle}>
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(width) ? 'h5' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Explore ${
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
          <ActiveFilters variant="default" />
        </div>
        <AllFilters
          open={showFilter}
          sortOptions={['popularity', 'recent', 'rating|imdb']}
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
              {popular.map((item, index) => {
                if (item) {
                  return <ItemCard key={item.id} itemId={item.id} />;
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
          !loading && (
            <Typography>Sorry, nothing matches your filter.</Typography>
          )
        )}
      </div>
    ) : null;
  };

  return (
    <ScrollToTopContainer>
      <div className={classes.popularWrapper}>
        {loading && renderLoading()}
        <Featured featuredItems={featuredItems} />
        {renderPopular()}
      </div>
    </ScrollToTopContainer>
  );
}

// DEV MODE ONLY
// Explore.whyDidYouRender = true;

export default React.memo(Explore);
