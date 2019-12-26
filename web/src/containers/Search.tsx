import React, { Component } from 'react';
import {
  createStyles,
  Grid,
  IconButton,
  CircularProgress,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  withWidth,
} from '@material-ui/core';
import { Tune } from '@material-ui/icons';
import * as R from 'ramda';
import { connect } from 'react-redux';
import { search, SearchInitiatedPayload } from '../actions/search';
import { bindActionCreators, Dispatch } from 'redux';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { Error as ErrorIcon } from '@material-ui/icons';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { Item } from '../types/v2/Item';
import { Genre, Network } from '../types';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import CreateSmartListButton from '../components/Buttons/CreateSmartListButton';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilter,
} from '../utils/urlHelper';
import { filterParamsEqual } from '../utils/changeDetection';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import SearchInput from '../components/Toolbar/Search';

const styles = (theme: Theme) =>
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
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  error: boolean;
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Item[];
  currentSearchText?: string;
  searchBookmark?: string;
}

interface DispatchProps {
  search: (payload: SearchInitiatedPayload) => void;
}

interface RouteParams {
  id: string;
}

interface StateProps {
  genres?: Genre[];
  networks?: Network[];
}

interface WidthProps {
  width: string;
}

type Props = OwnProps &
  WithUserProps &
  DispatchProps &
  StateProps &
  WidthProps &
  RouteComponentProps<RouteParams>;

type State = {
  searchText: string;
  genres?: Genre[];
  filters: FilterParams;
  showFilter: boolean;
  createDynamicListDialogOpen: boolean;
  totalLoadedImages: number;
};

class Search extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    let filterParams = DEFAULT_FILTER_PARAMS;
    let paramsFromQuery = parseFilterParamsFromQs(props.location.search);

    if (paramsFromQuery.sortOrder === 'default') {
      paramsFromQuery.sortOrder = 'popularity';
    }

    filterParams = {
      ...filterParams,
      ...paramsFromQuery,
    };

    let params = new URLSearchParams(window.location.search);
    let query;
    let param = params.get('q');

    query = param && param.length > 0 ? decodeURIComponent(param) : '';

    this.state = {
      ...this.state,
      showFilter: false,
      searchText: query,
      filters: filterParams,
      createDynamicListDialogOpen: false,
      totalLoadedImages: 0,
    };

    if (this.props.currentSearchText !== query) {
      this.loadResults();
    }
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }

  debouncedSearch = _.debounce(() => {
    this.loadResults();
  }, 200);

  loadResults() {
    const {
      filters: { itemTypes, genresFilter, networks, sliders },
      searchText,
    } = this.state;
    const { searchBookmark, width } = this.props;

    // To do: add support for sorting
    if (!this.props.isSearching) {
      this.props.search({
        query: searchText,
        bookmark: searchBookmark ? searchBookmark : undefined,
        limit: calculateLimit(width, 3, 0),
        itemTypes,
        networks,
        genres: genresFilter,
        releaseYearRange:
          sliders && sliders.releaseYear
            ? {
                min: sliders.releaseYear.min,
                max: sliders.releaseYear.max,
              }
            : undefined,
      });
    }
  }

  loadMoreResults = () => {
    const { totalLoadedImages } = this.state;
    const { isSearching, searchResults, width } = this.props;
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems = (searchResults && searchResults.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const shouldLoadMore = totalNonLoadedImages <= numColumns;

    if (!isSearching && shouldLoadMore) {
      this.debouncedSearch();
    }
  };

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  renderLoading = () => {
    const { classes } = this.props;

    return (
      <div className={classes.progressSpinner}>
        <CircularProgress />
      </div>
    );
  };

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      this.setState(
        {
          filters: filterParams,
        },
        () => {
          updateUrlParamsForFilter(this.props, filterParams);
          this.loadMoreResults();
        },
      );
    }
  };

  createListFromFilters = () => {
    this.setState({
      createDynamicListDialogOpen: true,
    });
  };

  handleCreateDynamicModalClose = () => {
    this.setState({
      createDynamicListDialogOpen: false,
    });
  };

  mapGenre = (genre: number) => {
    const { genres } = this.props;
    const genreItem = genres && genres.find(obj => obj.id === genre);

    return (genreItem && genreItem.name) || '';
  };

  render() {
    let {
      classes,
      currentSearchText,
      genres,
      searchBookmark,
      searchResults,
      userSelf,
    } = this.props;

    const {
      filters: { itemTypes, genresFilter, networks, sliders },
    } = this.state;

    let firstLoad = !searchResults;
    searchResults = searchResults || [];

    return (
      <React.Fragment>
        <div className={classes.searchResultsContainer}>
          <div style={{ margin: 48 }}>
            <Typography
              color="inherit"
              variant="h2"
              align="center"
              style={{ margin: 16 }}
            >
              Search
            </Typography>
            <SearchInput
              inputStyle={{ height: 50 }}
              filters={this.state.filters}
              quickSearchColor={'secondary'}
            />
          </div>
          <div className={classes.listTitle}>
            <Typography
              color="inherit"
              variant={['xs', 'sm'].includes(this.props.width) ? 'h6' : 'h4'}
              style={{ flexGrow: 1 }}
            >
              {`${
                genresFilter && genresFilter.length === 1
                  ? this.mapGenre(genresFilter[0])
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
              onClick={this.toggleFilters}
              className={classes.settings}
              color={this.state.showFilter ? 'primary' : 'inherit'}
            >
              <Tune />
              <Typography variant="srOnly">Tune</Typography>
            </IconButton>
            <CreateSmartListButton onClick={this.createListFromFilters} />
          </div>
          <div className={classes.filters}>
            <ActiveFilters
              genres={genres}
              updateFilters={this.handleFilterParamsChange}
              isListDynamic={false}
              filters={this.state.filters}
              variant="default"
            />
          </div>
          <AllFilters
            genres={genres}
            open={this.state.showFilter}
            filters={this.state.filters}
            updateFilters={this.handleFilterParamsChange}
            sortOptions={['popularity', 'recent']}
          />
          {this.props.isSearching &&
          (!searchBookmark || this.state.searchText !== currentSearchText) ? (
            this.renderLoading()
          ) : !this.props.error ? (
            searchResults && searchResults.length > 0 ? (
              <React.Fragment>
                <InfiniteScroll
                  pageStart={0}
                  loadMore={() => this.loadMoreResults()}
                  hasMore={Boolean(searchBookmark)}
                  useWindow
                  threshold={300}
                >
                  <Grid container spacing={2}>
                    {searchResults.map(result => {
                      return (
                        <ItemCard
                          key={result.id}
                          userSelf={userSelf}
                          item={result}
                          hasLoaded={this.setVisibleItems}
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
        </div>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    currentSearchText: R.path<string>(
      ['search', 'currentSearchText'],
      appState,
    ),
    isSearching: appState.search.searching,
    // TODO: Pass SearchResult object that either contains error or a response
    error: appState.search.error,
    genres: appState.metadata.genres,
    searchResults: appState.search.results,
    searchBookmark: appState.search.bookmark,
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      search,
    },
    dispatch,
  );
};

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(connect(mapStateToProps, mapDispatchToProps)(Search)),
    ),
  ),
);
