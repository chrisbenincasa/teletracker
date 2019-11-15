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
import { Genre, ItemType, SortOptions, NetworkType } from '../types';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilter,
} from '../utils/urlHelper';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import { SliderChange } from '../components/Filters/Sliders';

const styles = (theme: Theme) =>
  createStyles({
    networkIcon: {
      width: 20,
      borderRadius: '50%',
    },
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
      padding: theme.spacing(3),
      display: 'flex',
      flexDirection: 'column',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: 8,
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  bookmark?: string;
  isAuthed: boolean;
  isSearching: boolean;
  loading: boolean;
  popular?: string[];
  thingsBySlug: { [key: string]: Item };
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
  totalLoadedImages: number;
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
      totalLoadedImages: 0,
    };
  }

  loadPopular(passBookmark: boolean, firstRun?: boolean) {
    const {
      filters: { itemTypes, sortOrder, genresFilter, networks, sliders },
    } = this.state;
    const { bookmark, retrievePopular, width } = this.props;

    // To do: add support for sorting
    if (!this.props.loading) {
      retrievePopular({
        bookmark: passBookmark ? bookmark : undefined,
        itemTypes,
        limit: calculateLimit(width, 3, firstRun ? 1 : 0),
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
    const { isLoggedIn, userSelf } = this.props;

    this.loadPopular(false, true);

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

  getHighestRated = () => {
    const { popular, thingsBySlug } = this.props;

    return popular
      ? popular.filter(item => {
          const thing = thingsBySlug[item];
          const voteAverage =
            thing.ratings && thing.ratings.length
              ? thing.ratings[0].vote_average
              : 0;
          const voteCount =
            (thing.ratings && thing.ratings.length
              ? thing.ratings[0].vote_count
              : 0) || 0;
          return voteAverage > 7 && voteCount > 1000;
        })
      : [];
  };

  componentDidUpdate(prevProps: Props, prevState: State) {
    const { loading, popular, thingsBySlug } = this.props;
    const { featuredItemsIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if (
      (!prevProps.popular && popular && !loading) ||
      (popular && featuredItemsIndex.length === 0 && !loading) ||
      (popular &&
        ['xs', 'sm'].includes(prevProps.width) !==
          ['xs', 'sm'].includes(this.props.width))
    ) {
      const highestRated = this.getHighestRated();
      const randomItemOne = Math.floor(Math.random() * highestRated.length);
      let randomItemTwo = Math.floor(Math.random() * highestRated.length);
      // In the event these two id's match
      do {
        randomItemTwo = Math.floor(Math.random() * highestRated.length);
      } while (randomItemOne === randomItemTwo);

      // I have no idea what this was for lol
      if (randomItemOne === 0 || randomItemTwo === 0) {
        if (this.state.featuredItemsIndex.length > 0) {
          this.setState({
            featuredItemsIndex: [],
          });
        }
      } else {
        const popularItemOne = popular.findIndex(
          name => name === highestRated[randomItemOne],
        );

        if (['xs', 'sm'].includes(this.props.width)) {
          this.setState({
            featuredItemsIndex: [popularItemOne],
          });
        } else {
          const popularItemTwo = popular.findIndex(
            name => name === highestRated[randomItemTwo],
          );

          this.setState({
            featuredItemsIndex: [popularItemOne, popularItemTwo],
            featuredItems: [popularItemOne, popularItemTwo].map(
              index => thingsBySlug[popular[index]],
            ),
          });
        }
      }
    }
  }

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      this.setState(
        {
          filters: filterParams,
        },
        () => {
          updateUrlParamsForFilter(this.props, filterParams);
          this.loadPopular(false);
        },
      );
    }
  };

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
    this.loadPopular(true);
  }, 250);

  loadMoreResults = () => {
    const { featuredItemsIndex, totalLoadedImages } = this.state;
    const { loading, popular, width } = this.props;
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems =
      (popular && popular.length - featuredItemsIndex.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const loadMore = totalNonLoadedImages <= numColumns;

    if (!loading && loadMore) {
      this.debounceLoadMore();
    }
  };

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  renderPopular = () => {
    const { classes, genres, popular, userSelf, thingsBySlug } = this.props;
    const {
      filters: { genresFilter, itemTypes },
    } = this.state;

    return popular && popular && popular.length ? (
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
            color={this.state.showFilter ? 'secondary' : 'inherit'}
          >
            <Tune />
            <Typography variant="srOnly">Tune</Typography>
          </IconButton>
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={this.handleFilterParamsChange}
            isListDynamic={false}
            filters={this.state.filters}
          />
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          filters={this.state.filters}
          updateFilters={this.handleFilterParamsChange}
          sortOptions={['popularity', 'recent']}
        />
        <InfiniteScroll
          pageStart={0}
          loadMore={() => this.loadMoreResults()}
          hasMore={Boolean(this.props.bookmark)}
          useWindow
          threshold={300}
        >
          <Grid container spacing={2}>
            {popular.map((result, index) => {
              let thing = thingsBySlug[result];
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
        </InfiniteScroll>
      </div>
    ) : null;
  };

  render() {
    const { featuredItems } = this.state;
    const { popular, thingsBySlug } = this.props;

    return popular ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        <Featured featuredItems={this.state.featuredItems} />
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
    thingsBySlug: appState.itemDetail.thingsBySlug,
    loading: appState.popular.loadingPopular,
    genres: appState.metadata.genres,
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
