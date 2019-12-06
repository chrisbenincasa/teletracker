import {
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
import * as R from 'ramda';
import React, { Component } from 'react';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { retrievePopular } from '../actions/popular';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import Featured from '../components/Featured';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { Genre, Network } from '../types';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilter,
} from '../utils/urlHelper';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import { getVoteAverage, getVoteCount } from '../utils/textHelper';
import CreateDynamicListDialog from '../components/Dialogs/CreateDynamicListDialog';
import CreateSmartListButton from '../components/Buttons/CreateSmartListButton';

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
      marginBottom: theme.spacing(1),
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
  RouteComponentProps<RouteParams>;

interface State {
  featuredItemsIndex: number[];
  featuredItems: Item[];
  showFilter: boolean;
  filters: FilterParams;
  needsNewFeatured: boolean;
  totalLoadedImages: number;
  createDynamicListDialogOpen: boolean;
}

class Popular extends Component<Props, State> {
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

    this.state = {
      ...this.state,
      featuredItemsIndex: [],
      featuredItems: [],
      showFilter: false,
      filters: filterParams,
      needsNewFeatured: false,
      totalLoadedImages: 0,
      createDynamicListDialogOpen: false,
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
      filters: { itemTypes, genresFilter, networks, sliders },
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
      });
    }
  }

  componentDidMount() {
    const { isLoggedIn, popular, userSelf } = this.props;

    if (!popular) {
      this.loadPopular(false, true);
    }

    ReactGA.initialize(GA_TRACKING_ID);
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
          updateUrlParamsForFilter(this.props, filterParams);
          this.loadPopular(false, true);
        },
      );
    }
  };

  componentDidUpdate(prevProps: Props) {
    const { loading, popular } = this.props;
    const { needsNewFeatured } = this.state;
    const isInitialFetch = popular && !prevProps.popular && !loading;
    const didScreenResize =
      popular &&
      ['xs', 'sm'].includes(prevProps.width) !==
        ['xs', 'sm'].includes(this.props.width);
    const didFilterChange =
      popular && prevProps.loading && !loading && needsNewFeatured;

    let paramsFromQuery = parseFilterParamsFromQs(this.props.location.search);

    // Checks if filters have changed, if so, update state and re-fetch popular
    this.handleFilterParamsChange(paramsFromQuery);

    if (isInitialFetch || didFilterChange || didScreenResize) {
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

  loadMoreResults = () => {
    const { featuredItemsIndex, totalLoadedImages } = this.state;
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

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  renderPopular = () => {
    const { classes, genres, popular, userSelf, thingsById } = this.props;
    const {
      filters: { genresFilter, itemTypes },
      createDynamicListDialogOpen,
    } = this.state;

    return popular ? (
      <div className={classes.popularContainer}>
        <div className={classes.listTitle}>
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(this.props.width) ? 'h6' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Popular ${
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
        {popular.length > 0 ? (
          <InfiniteScroll
            pageStart={0}
            loadMore={() => this.loadMoreResults()}
            hasMore={Boolean(this.props.bookmark)}
            useWindow
            threshold={300}
          >
            <Grid container spacing={2}>
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
        <CreateDynamicListDialog
          filters={this.state.filters}
          open={createDynamicListDialogOpen}
          onClose={this.handleCreateDynamicModalClose}
          networks={this.props.networks || []}
          genres={this.props.genres || []}
        />
      </div>
    ) : null;
  };

  render() {
    const { featuredItems } = this.state;
    const { classes, popular } = this.props;

    return popular ? (
      <div className={classes.popularWrapper}>
        <Featured featuredItems={featuredItems} />
        {this.renderPopular()}
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
