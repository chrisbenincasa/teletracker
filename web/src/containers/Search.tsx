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
import _ from 'lodash';
import { useRouter } from 'next/router';
import qs from 'querystring';
import * as R from 'ramda';
import React, { useCallback, useEffect, useRef, useState } from 'react';
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
import { useStateDeepEqWithPrevious } from '../hooks/useStateDeepEq';
import { useWidth } from '../hooks/useWidth';
import { useWithUserContext } from '../hooks/useWithUser';
import { AppState } from '../reducers';
import { filterParamsEqual } from '../utils/changeDetection';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';
import { useDebouncedCallback } from 'use-debounce';

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

const Search = ({ inViewportChange }) => {
  const classes = useStyles();
  const width = useWidth();
  const withUserState = useWithUserContext();
  const dispatch = useDispatch();
  const router = useRouter();

  const currentSearchText = useSelector((state: AppState) =>
    R.path<string>(['search', 'currentSearchText'], state),
  );
  const isSearching = useSelector((state: AppState) => state.search.searching);
  const error = useSelector((state: AppState) => state.search.error);
  const genres = useSelector((state: AppState) => state.metadata.genres);
  const searchResults = useSelector((state: AppState) => state.search.results);
  const searchBookmark = useSelector(
    (state: AppState) => state.search.bookmark,
  );

  const [showScrollToTop, setShowScrollToTop] = useState<boolean>(false);
  const [totalVisibleItems, setTotalVisibleItems] = useState<number>(0);
  const [showFilter, setShowFilter] = useState<boolean>(false);
  const [searchText, setSearchText] = useState<string>(currentSearchText || '');
  const [filters, setFilters, previousFilters] = useStateDeepEqWithPrevious(
    DEFAULT_FILTER_PARAMS,
    filterParamsEqual,
  );

  const searchWrapperRef = useRef<HTMLDivElement>(null);

  const { itemTypes, genresFilter, networks, sliders, sortOrder } = filters;

  const isInViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: `0px`,
      threshold: 1.0,
    },
    targetRef: searchWrapperRef,
    useLazyLoad: false,
  });

  // Initial Load
  useEffect(() => {
    let filterParams = DEFAULT_FILTER_PARAMS;
    let queryString = qs.stringify(router.query);
    let paramsFromQuery = parseFilterParamsFromQs(queryString);

    // How will this work with SSR
    // ReactGA.pageview(location.pathname + location.search);

    if (_.isUndefined(paramsFromQuery.sortOrder)) {
      paramsFromQuery.sortOrder = 'popularity';
    }

    filterParams = {
      ...filterParams,
      ...paramsFromQuery,
    };

    let params = new URLSearchParams(queryString);
    let query;
    let param = params.get('q');

    query = param && param.length > 0 ? decodeURIComponent(param) : '';

    setSearchText(query);
    setFilters(filterParams);
  }, []);

  const onScroll = () => {
    const scrollTop = window.pageYOffset || 0;
    // to do: 100 is just a random number, we can play with this or make it dynamic
    if (scrollTop > 100 && !showScrollToTop) {
      setShowScrollToTop(true);
    } else if (scrollTop < 100 && showScrollToTop) {
      setShowScrollToTop(false);
    }
  };

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
  useEffect(() => {
    if (
      previousFilters &&
      !filterParamsEqual(previousFilters, filters, 'popularity')
    ) {
      debouncedSearch(true);
    }
  }, [filters, previousFilters, searchBookmark]);

  // Run callback when search enters/leaves viewport
  useEffect(() => {
    inViewportChange(isInViewport);
    // return inViewportChange(false);
  }, [isInViewport]);

  const loadMoreResults = () => {
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems = (searchResults && searchResults.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalVisibleItems;
    const shouldLoadMore = totalNonLoadedImages <= numColumns;

    if (!isSearching && shouldLoadMore) {
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

  const handleFilterParamsChange = (filterParams: FilterParams) => {
    setFilters(filterParams);
  };

  const mapGenre = (genre: number) => {
    const genreItem = genres && genres.find(obj => obj.id === genre);

    return (genreItem && genreItem.name) || '';
  };

  let firstLoad = !searchResults;

  const showSearch =
    !currentSearchText || (currentSearchText && currentSearchText.length === 0);
  // || isInViewport;
  const loadHandler = useCallback(
    () => setTotalVisibleItems(prev => prev + 1),
    [],
  );

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
                genres={genres}
                updateFilters={handleFilterParamsChange}
                isListDynamic={false}
                filters={filters}
                variant="default"
                hideSortOptions
              />
            </div>
            <AllFilters
              genres={genres}
              open={showFilter}
              filters={filters}
              disableSortOptions
              updateFilters={handleFilterParamsChange}
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
                        return (
                          <ItemCard
                            key={result.id}
                            itemId={result.id}
                            hasLoaded={loadHandler}
                          />
                        );
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
