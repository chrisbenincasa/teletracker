import {
  Button,
  CircularProgress,
  createStyles,
  Grid,
  IconButton,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
import { Tune } from '@material-ui/icons';
import _ from 'lodash';
import { WithRouterProps } from 'next/dist/client/with-router';
import { withRouter } from 'next/router';
import qs from 'querystring';
import * as R from 'ramda';
import React, { Component } from 'react';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { retrievePopular } from '../actions/popular';
import { PopularInitiatedActionPayload } from '../actions/popular';
import Featured from '../components/Featured';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ItemCard from '../components/ItemCard';
import ScrollToTop from '../components/Buttons/ScrollToTop';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Genre, Network } from '../types';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { getVoteAverage, getVoteCount } from '../utils/textHelper';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilterRouter,
} from '../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
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
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  bookmark?: string;
  isAuthed: boolean;
  isSearching: boolean;
  loading: boolean;
  popular?: string[];
  thingsById: { [key: string]: Item };
}

interface RouteParams {
  id: string;
}

interface WidthProps {
  width: string;
}

interface DispatchProps {
  retrievePopular: (payload: PopularInitiatedActionPayload) => void;
}

interface StateProps {
  genres?: Genre[];
  networks?: Network[];
}

type Props = OwnProps &
  InjectedProps &
  DispatchProps &
  WithUserProps &
  WidthProps &
  StateProps &
  WithRouterProps;

interface State {
  featuredItemsIndex: number[];
  featuredItems: Item[];
  showFilter: boolean;
  filters: FilterParams;
  needsNewFeatured: boolean;
  totalLoadedImages: number;
  showScrollToTop: boolean;
}

class Popular extends Component<Props, State> {
  private popularWrapper: React.RefObject<HTMLDivElement>;

  constructor(props: Props) {
    super(props);
    this.onScroll = this.onScroll.bind(this);
    this.popularWrapper = React.createRef();

    let filterParams = DEFAULT_FILTER_PARAMS;
    let paramsFromQuery = parseFilterParamsFromQs(
      qs.stringify(props.router.query),
    );

    if (paramsFromQuery.sortOrder === 'default') {
      paramsFromQuery.sortOrder = 'popularity';
    }

    filterParams = {
      ...filterParams,
      ...paramsFromQuery,
    };

    this.state = {
      ...this.state,
      featuredItemsIndex: [],
      featuredItems: [],
      showFilter: false,
      filters: filterParams,
      needsNewFeatured: false,
      totalLoadedImages: 0,
      showScrollToTop: false,
    };
  }

  getNumberFeaturedItems = () => {
    if (['xs', 'sm'].includes(this.props.width)) {
      return 1;
    } else {
      return 2;
    }
  };

  loadPopular(passBookmark: boolean, firstRun?: boolean) {
    const {
      filters: { itemTypes, genresFilter, networks, sliders, people },
    } = this.state;
    const { bookmark, retrievePopular, width } = this.props;

    // To do: add support for sorting
    if (!this.props.loading) {
      let numberFeaturedItems: number = this.getNumberFeaturedItems();

      retrievePopular({
        bookmark: passBookmark ? bookmark : undefined,
        itemTypes,
        limit: calculateLimit(width, 3, firstRun ? numberFeaturedItems : 0),
        networks,
        genres: genresFilter,
        releaseYearRange:
          sliders && sliders.releaseYear
            ? {
                min: sliders.releaseYear.min,
                max: sliders.releaseYear.max,
              }
            : undefined,
        castIncludes: people,
      });
    }
  }

  componentDidMount() {
    const { isLoggedIn, popular, userSelf } = this.props;

    if (!popular) {
      this.loadPopular(false, true);
    } else {
      this.setState({
        needsNewFeatured: true,
      });
    }

    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }

    window.addEventListener('scroll', this.onScroll, false);
  }

  componentWillUnmount() {
    window.removeEventListener('scroll', this.onScroll, false);
  }

  onScroll = () => {
    const scrollTop = window.pageYOffset || 0;
    // to do: 100 is just a random number, we can play with this or make it dynamic
    if (scrollTop > 100 && !this.state.showScrollToTop) {
      this.setState({ showScrollToTop: true });
    } else if (scrollTop < 100 && this.state.showScrollToTop) {
      this.setState({ showScrollToTop: false });
    }
  };

  getFeaturedItems = numberFeaturedItems => {
    const { popular, thingsById, width } = this.props;

    // We'll use the initialLoadSize to slice the array to ensure the featured items don't change as the popular array size increases
    const initialLoadSize = calculateLimit(width, 3, numberFeaturedItems);

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
  };

  setFeaturedItems = () => {
    const { popular, thingsById, width } = this.props;
    const { featuredItemsIndex } = this.state;
    let numberFeaturedItems: number = this.getNumberFeaturedItems();
    const featuredItems: string[] = this.getFeaturedItems(numberFeaturedItems);

    // Require that there be at least 2 full rows before displaying Featured items.
    const featuredRequiredItems = calculateLimit(width, 2, numberFeaturedItems);

    // If we don't have enough content to fill featured items, don't show any
    if (
      featuredItems.length < numberFeaturedItems ||
      popular!.length < featuredRequiredItems
    ) {
      // Prevent re-setting state if it's already been reset
      if (featuredItemsIndex.length > 0) {
        this.setState({
          featuredItemsIndex: [],
          featuredItems: [],
          needsNewFeatured: false,
        });
      }
    } else {
      const featuredIndexes = featuredItems.map(item =>
        popular!.findIndex(id => item === id),
      );

      this.setState({
        featuredItemsIndex: featuredIndexes,
        featuredItems: featuredIndexes.map(
          index => thingsById[popular![index]],
        ),
        needsNewFeatured: false,
      });
    }
  };

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      this.setState(
        {
          filters: filterParams,
          needsNewFeatured: true,
        },
        () => {
          updateUrlParamsForFilterRouter(this.props, filterParams);
          this.loadPopular(false, true);
        },
      );
    }
  };

  componentDidUpdate(prevProps: Props) {
    const { loading, router, popular } = this.props;
    const { needsNewFeatured } = this.state;

    let query = qs.stringify(router.query);
    let prevQuery = qs.stringify(prevProps.router.query);

    // Initial mount of Popular
    const isInitialFetch = popular && !prevProps.popular && !loading;

    // Any new fetches (e.g. filtering popular by type, genre, etc)
    const isNewFetch = popular && prevProps.loading && !loading;

    // Re-sizing to sm,xs changes # of featured items
    const didScreenResize =
      popular &&
      ['xs', 'sm'].includes(prevProps.width) !==
        ['xs', 'sm'].includes(this.props.width);

    // User navigated back to page or via top nav
    const didNavigate = !isInitialFetch && needsNewFeatured;

    let paramsFromQuery = parseFilterParamsFromQs(query);

    // Checks if filters have changed, if so, update state and re-fetch popular
    if (query !== prevQuery) {
      this.handleFilterParamsChange(paramsFromQuery);
    }

    if (isInitialFetch || isNewFetch || didScreenResize || didNavigate) {
      this.setFeaturedItems();
    }
  }

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  mapGenre = (genre: number) => {
    const { genres } = this.props;
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  renderLoading = () => {
    const { classes } = this.props;

    return (
      <div className={classes.loadingBar}>
        <LinearProgress />
      </div>
    );
  };

  renderLoadingCircle() {
    const { classes } = this.props;
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  }

  debounceLoadMore = _.debounce(() => {
    this.loadPopular(true, false);
  }, 250);

  scrollToTop = () => {
    window.scrollTo(0, 0);
    this.setState({
      showScrollToTop: false,
    });
  };

  loadMoreResults = () => {
    const {
      featuredItemsIndex,
      showScrollToTop,
      totalLoadedImages,
    } = this.state;
    const { loading, popular, width } = this.props;
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems =
      (popular && popular.length - featuredItemsIndex.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const shouldLoadMore = totalNonLoadedImages <= numColumns;

    if (!loading && shouldLoadMore) {
      this.debounceLoadMore();
    }
    if (!showScrollToTop) {
      this.setState({
        showScrollToTop: true,
      });
    }
  };

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  renderPopular = () => {
    const { classes, genres, popular, userSelf, thingsById } = this.props;
    const {
      filters: { genresFilter, itemTypes },
    } = this.state;
    const filtersCTA = this.state.showFilter ? 'Hide Filters' : 'Filters';

    return popular ? (
      <div className={classes.popularContainer}>
        <div className={classes.listTitle}>
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(this.props.width) ? 'h6' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Trending ${
              genresFilter && genresFilter.length === 1
                ? this.mapGenre(genresFilter[0])
                : ''
            } ${
              itemTypes && itemTypes.length === 1
                ? itemTypes.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
          <Button
            size="small"
            onClick={this.toggleFilters}
            variant="contained"
            aria-label={filtersCTA}
            startIcon={<Tune />}
            style={{ whiteSpace: 'nowrap' }}
          >
            {filtersCTA}
          </Button>
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={this.handleFilterParamsChange}
            isListDynamic={false}
            filters={this.state.filters}
            variant="default"
            hideSortOptions //Need better solution to just hide popular by default
          />
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          filters={this.state.filters}
          updateFilters={this.handleFilterParamsChange}
          sortOptions={['popularity', 'recent']}
        />
        {popular.length > 0 ? (
          <InfiniteScroll
            pageStart={0}
            loadMore={() => this.loadMoreResults()}
            hasMore={Boolean(this.props.bookmark)}
            useWindow
            threshold={300}
          >
            <Grid container spacing={2} ref={this.popularWrapper}>
              {popular.map((result, index) => {
                let thing = thingsById[result];
                if (thing && !this.state.featuredItemsIndex.includes(index)) {
                  return (
                    <ItemCard
                      key={result}
                      userSelf={userSelf}
                      item={thing}
                      hasLoaded={this.setVisibleItems}
                    />
                  );
                } else {
                  return null;
                }
              })}
            </Grid>
            {this.props.loading && this.renderLoadingCircle()}
            {!Boolean(this.props.bookmark) && (
              <Typography className={classes.fin}>fin.</Typography>
            )}
          </InfiniteScroll>
        ) : (
          <Typography>Sorry, nothing matches your filter.</Typography>
        )}
      </div>
    ) : null;
  };

  render() {
    const { featuredItems, showScrollToTop } = this.state;
    const { classes, popular } = this.props;

    return popular ? (
      <div className={classes.popularWrapper}>
        <Featured featuredItems={featuredItems} />
        {this.renderPopular()}
        {showScrollToTop && (
          <ScrollToTop
            onClick={this.scrollToTop}
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
      this.renderLoading()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    popular: appState.popular.popular,
    thingsById: appState.itemDetail.thingsById,
    loading: appState.popular.loadingPopular,
    genres: appState.metadata.genres,
    networks: appState.metadata.networks,
    bookmark: appState.popular.popularBookmark,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(connect(mapStateToProps, mapDispatchToProps)(Popular)),
    ),
  ),
);
