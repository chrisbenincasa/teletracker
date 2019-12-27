import React, { useEffect, useRef, useState } from 'react';
import {
  CircularProgress,
  createStyles,
  Fade,
  Grid,
  IconButton,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { Tune } from '@material-ui/icons';
import * as R from 'ramda';
import { useDispatch, useSelector } from 'react-redux';
import { search } from '../actions/search';
import ItemCard from '../components/ItemCard';
import { AppState } from '../reducers';
import { useLocation } from 'react-router-dom';
import { Error as ErrorIcon } from '@material-ui/icons';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { Genre } from '../types';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import CreateSmartListButton from '../components/Buttons/CreateSmartListButton';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';
import { filterParamsEqual } from '../utils/changeDetection';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import SearchInput from '../components/Toolbar/Search';
import { useWidth } from '../hooks/useWidth';
import { useWithUser } from '../hooks/useWithUser';
import { useStateDeepEqWithPrevious } from '../hooks/useStateDeepEq';
import useIntersectionObserver from '../hooks/useIntersectionObserver';

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
      flexGrow: 1,
      justifyContent: 'center',
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
      margin: theme.spacing(1),
      padding: theme.spacing(1),
      width: '100%',
      marginTop: theme.spacing(5),
    },
    searchTitle: {
      margin: theme.spacing(2),
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
  }),
);

interface Props {}

type State = {
  searchText: string;
  genres?: Genre[];
  filters: FilterParams;
  showFilter: boolean;
  createDynamicListDialogOpen: boolean;
  totalLoadedImages: number;
};

const Search = ({ inViewportChange }) => {
  const classes = useStyles();
  const width = useWidth();
  const withUserState = useWithUser();
  const location = useLocation();
  const dispatch = useDispatch();

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

  const [totalVisibleItems, setTotalVisibleItems] = useState<number>(0);
  const [
    createDynamicListDialogOpen,
    setCreateDynamicListDialogOpen,
  ] = useState<boolean>(false);
  const [showFilter, setShowFilter] = useState<boolean>(false);
  const [searchText, setSearchText] = useState<string>('');
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
    let paramsFromQuery = parseFilterParamsFromQs(location.search);

    ReactGA.pageview(location.pathname + location.search);

    if (paramsFromQuery.sortOrder === 'default') {
      paramsFromQuery.sortOrder = 'popularity';
    }

    filterParams = {
      ...filterParams,
      ...paramsFromQuery,
    };

    let params = new URLSearchParams(location.search);
    let query;
    let param = params.get('q');

    query = param && param.length > 0 ? decodeURIComponent(param) : '';

    setSearchText(query);
    setFilters(filterParams);
  }, []);

  // Load new set of search results when searchText changes
  useEffect(() => {
    if (searchText !== currentSearchText && searchText.length > 0) {
      loadResults(true);
    }
  }, [searchText]);

  // Load new set of search results when filters change
  // Ignore initial load because previousFilters won't be defined
  useEffect(() => {
    if (!filterParamsEqual(previousFilters, filters) && previousFilters) {
      loadResults(true);
    }
  }, [filters, previousFilters, searchBookmark]);

  // Run callback when search enters/leaves viewport
  useEffect(() => {
    inViewportChange(isInViewport);
  }, [isInViewport]);

  const debouncedSearch = _.debounce(() => {
    loadResults(false);
  }, 200);

  const loadResults = (firstLoad?: boolean) => {
    // This is to handle the case where a query param doesn't exist, we'll want to use the redux state.
    // We won't want to rely on this initially because initial load is relying on query param not redux state.
    const fallbackSearchText = currentSearchText || '';

    if (!isSearching) {
      dispatch(
        search({
          query: firstLoad ? searchText : fallbackSearchText,
          bookmark: searchBookmark ? searchBookmark : undefined,
          limit: calculateLimit(width, 3, 0),
          itemTypes,
          networks,
          genres: genresFilter,
          sort: sortOrder === 'default' ? 'recent' : sortOrder,
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

  const loadMoreResults = () => {
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems = (searchResults && searchResults.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalVisibleItems;
    const shouldLoadMore = totalNonLoadedImages <= numColumns;

    if (!isSearching && shouldLoadMore) {
      debouncedSearch();
    }
  };

  const renderLoading = () => {
    return (
      <div className={classes.progressSpinner}>
        <CircularProgress />
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
  return (
    <React.Fragment>
      <div className={classes.searchResultsContainer}>
        <Fade in={isInViewport} ref={searchWrapperRef} timeout={500}>
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
              <IconButton
                onClick={toggleFilters}
                className={classes.settings}
                color={showFilter ? 'primary' : 'inherit'}
              >
                <Tune />
                <Typography variant="srOnly">Tune</Typography>
              </IconButton>
              <CreateSmartListButton
                onClick={() => setCreateDynamicListDialogOpen(true)}
              />
            </div>
            <div className={classes.filters}>
              <ActiveFilters
                genres={genres}
                updateFilters={handleFilterParamsChange}
                isListDynamic={false}
                filters={filters}
                variant="default"
              />
            </div>
            <AllFilters
              genres={genres}
              open={showFilter}
              filters={filters}
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
                    loadMore={() => loadMoreResults()}
                    hasMore={Boolean(searchBookmark)}
                    useWindow
                    threshold={300}
                  >
                    <Grid container spacing={2}>
                      {searchResults.map(result => {
                        return (
                          <ItemCard
                            key={result.id}
                            userSelf={withUserState.userSelf}
                            item={result}
                            hasLoaded={() =>
                              setTotalVisibleItems(totalVisibleItems + 1)
                            }
                          />
                        );
                      })}
                    </Grid>
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
          </React.Fragment>
        )}
      </div>
    </React.Fragment>
  );
};

export default Search;
