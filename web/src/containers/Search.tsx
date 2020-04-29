import {
  CircularProgress,
  createStyles,
  Fade,
  Grid,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { Error as ErrorIcon } from '@material-ui/icons';
import React, {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { useDispatch, useSelector } from 'react-redux';
import { search } from '../actions/search';
import ScrollToTop from '../components/Buttons/ScrollToTop';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ItemCard from '../components/ItemCard';
import SearchInput from '../components/Toolbar/Search';
import useIntersectionObserver from '../hooks/useIntersectionObserver';
import { useWidth } from '../hooks/useWidth';
import { AppState } from '../reducers';
import { calculateLimit } from '../utils/list-utils';
import { useDebouncedCallback } from 'use-debounce';
import useStateSelector from '../hooks/useStateSelector';
import { FilterContext } from '../components/Filters/FilterContext';
import useFilterLoadEffect from '../hooks/useFilterLoadEffect';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    listTitle: {
      display: 'flex',
      flexDirection: 'row',
      alignItems: 'center',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    progressSpinner: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    searchError: {
      display: 'flex',
      flexDirection: 'column',
      flexGrow: 1,
      alignItems: 'center',
      marginTop: theme.spacing(3),
    },
    searchNoResults: {
      display: 'flex',
      flexGrow: 1,
      justifyContent: 'center',
      margin: theme.spacing(1),
      padding: theme.spacing(1),
    },
    searchResultsContainer: {
      // margin: theme.spacing(1),
      padding: theme.spacing(2),
      width: '100%',
    },
    searchTitle: {
      margin: theme.spacing(2),
    },
  }),
);

interface Props {
  inViewportChange: (inViewport: boolean) => void;
  preloadedQuery?: string;
}

const Search = (props: Props) => {
  const classes = useStyles();
  const width = useWidth();
  const dispatch = useDispatch();

  const currentSearchText = useStateSelector(
    state => state.search.currentSearchText,
  );
  const isSearching = useSelector((state: AppState) => state.search.searching);
  const error = useSelector((state: AppState) => state.search.error);
  const genres = useSelector((state: AppState) => state.metadata.genres);
  const searchResults = useSelector((state: AppState) => state.search.results);
  const searchBookmark = useSelector(
    (state: AppState) => state.search.bookmark,
  );

  const [showScrollToTop, setShowScrollToTop] = useState(false);
  const [showFilter, setShowFilter] = useState(false);
  const [searchText, setSearchText] = useState(currentSearchText || '');
  const { filters } = useContext(FilterContext);
  const searchWrapperRef = useRef<HTMLDivElement>(null);

  const { itemTypes, genresFilter, networks, sliders } = filters;

  const isInViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: `0px`,
      threshold: 1.0,
    },
    targetRef: searchWrapperRef,
    useLazyLoad: false,
  });

  useEffect(() => {
    setSearchText(currentSearchText);
  }, [currentSearchText]);

  const onScroll = useCallback(() => {
    const scrollTop = window.pageYOffset || 0;
    // to do: 100 is just a random number, we can play with this or make it dynamic
    if (scrollTop > 100 && !showScrollToTop) {
      setShowScrollToTop(true);
    } else if (scrollTop < 100 && showScrollToTop) {
      setShowScrollToTop(false);
    }
  }, []);

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    setShowScrollToTop(false);
  };

  useEffect(() => {
    window.addEventListener('scroll', onScroll, false);
    return () => {
      window.removeEventListener('scroll', onScroll, false);
    };
  });

  const loadResults = (firstLoad?: boolean) => {
    // This is to handle the case where a query param doesn't exist, we'll want to use the redux state.
    // We won't want to rely on this initially because initial load is relying on query param not redux state.
    const fallbackSearchText = currentSearchText || '';

    if (!isSearching) {
      dispatch(
        search({
          query: firstLoad ? searchText : fallbackSearchText,
          bookmark: firstLoad ? undefined : searchBookmark,
          limit: calculateLimit(width, 3, 0),
          itemTypes,
          networks,
          genres: genresFilter,
          sort: 'search_score',
          releaseYearRange:
            sliders && sliders.releaseYear
              ? {
                  min: sliders.releaseYear.min,
                  max: sliders.releaseYear.max,
                }
              : undefined,
        }),
      );
    }
  };

  const [debouncedSearch] = useDebouncedCallback(loadResults, 200);

  // Load new set of search results when searchText changes
  useEffect(() => {
    if (searchText !== currentSearchText && searchText.length > 0) {
      debouncedSearch(true);
    }
  }, [searchText]);

  // Load new set of search results when filters change
  // Ignore initial load because previousFilters won't be defined
  useFilterLoadEffect(
    () => {
      debouncedSearch(true);
    },
    state => state.search.currentFilters,
  );

  // Run callback when search enters/leaves viewport
  useEffect(() => {
    props.inViewportChange(isInViewport);
  }, [isInViewport]);

  const loadMoreResults = () => {
    if (!isSearching) {
      debouncedSearch(false);
    }
  };

  const renderLoading = () => {
    return (
      <div className={classes.progressSpinner}>
        <div>
          <CircularProgress />
        </div>
      </div>
    );
  };

  const toggleFilters = () => {
    setShowFilter(!showFilter);
  };

  const mapGenre = (genre: number) => {
    const genreItem = genres && genres.find(obj => obj.id === genre);

    return (genreItem && genreItem.name) || '';
  };

  let firstLoad = !searchResults;

  const showSearch =
    !currentSearchText || (currentSearchText && currentSearchText.length === 0);
  // || isInViewport;

  return (
    <React.Fragment>
      <div className={classes.searchResultsContainer}>
        {showSearch && (
          <Fade in={showSearch} ref={searchWrapperRef} timeout={500}>
            <div>
              <Typography
                color="inherit"
                variant="h2"
                align="center"
                className={classes.searchTitle}
              >
                Search
              </Typography>

              <SearchInput
                inputStyle={{ height: 50 }}
                filters={filters}
                quickSearchColor={'secondary'}
              />
            </div>
          </Fade>
        )}

        {currentSearchText && currentSearchText.length > 0 && (
          <React.Fragment>
            <div className={classes.listTitle}>
              <Typography
                color="inherit"
                variant={['xs', 'sm'].includes(width) ? 'h6' : 'h4'}
                style={{ flexGrow: 1 }}
              >
                {`${
                  genresFilter && genresFilter.length === 1
                    ? mapGenre(genresFilter[0])
                    : ''
                } ${
                  itemTypes && itemTypes.length === 1
                    ? itemTypes.includes('movie')
                      ? 'Movies'
                      : 'TV Shows'
                    : 'Content'
                } that matches "${currentSearchText}"`}
              </Typography>
              <ShowFiltersButton onClick={toggleFilters} />
            </div>
            <div className={classes.filters}>
              <ActiveFilters
                isListDynamic={false}
                variant="default"
                hideSortOptions
              />
            </div>
            <AllFilters
              open={showFilter}
              disableSortOptions
              sortOptions={['popularity', 'recent']}
            />
            {isSearching && !searchBookmark ? (
              renderLoading()
            ) : !error ? (
              searchResults && searchResults.length > 0 ? (
                <React.Fragment>
                  <InfiniteScroll
                    pageStart={0}
                    loadMore={loadMoreResults}
                    hasMore={Boolean(searchBookmark)}
                    useWindow
                    threshold={300}
                  >
                    <Grid container spacing={2}>
                      {searchResults.map(result => {
                        return <ItemCard key={result.id} itemId={result.id} />;
                      })}
                    </Grid>
                    {isSearching && renderLoading()}
                  </InfiniteScroll>
                </React.Fragment>
              ) : firstLoad ? null : (
                <div className={classes.searchNoResults}>
                  <Typography variant="h5" gutterBottom align="center">
                    No results :(
                  </Typography>
                </div>
              )
            ) : (
              <div className={classes.searchError}>
                <ErrorIcon color="inherit" fontSize="large" />
                <Typography variant="h5" gutterBottom align="center">
                  Something went wrong :(
                </Typography>
              </div>
            )}
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
          </React.Fragment>
        )}
      </div>
    </React.Fragment>
  );
};

export default Search;
