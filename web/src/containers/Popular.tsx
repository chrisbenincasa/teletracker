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
import { Genre, ItemType, ListSortOptions, NetworkType } from '../types';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';
import { calculateLimit, getNumColumns } from '../utils/list-utils';

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
      padding: 8,
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
  mainItemIndex: number;
  showFilter: boolean;
  filters: FilterParams;
  totalLoadedImages: number;
}

class Popular extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    let filterParams = parseFilterParamsFromQs(props.location.search);

    this.state = {
      ...this.state,
      mainItemIndex: -1,
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

  componentDidUpdate(prevProps: Props, prevState: State) {
    const { popular, thingsBySlug } = this.props;
    const { mainItemIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
      const highestRated = popular.filter(item => {
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
      });

      const randomItem = Math.floor(Math.random() * highestRated.length);
      if (randomItem === 0) {
        this.setState({
          mainItemIndex: 0,
        });
      } else {
        const popularItem = popular.findIndex(
          name => name === highestRated[randomItem],
        );

        this.setState({
          mainItemIndex: popularItem,
        });
      }
    }

    if (this.props.location.search !== prevProps.location.search) {
      let filters = parseFilterParamsFromQs(this.props.location.search);
      if (!filterParamsEqual(filters, this.state.filters)) {
        this.setFilters(filters);
      }
    }
  }

  setType = (itemTypes?: ItemType[]) => {
    // Only update and hit endpoint if there is a state change
    if (
      _.xor(this.state.filters.itemTypes || [], itemTypes || []).length !== 0
    ) {
      this.setState(
        {
          filters: {
            ...this.state.filters,
            itemTypes,
          },
        },
        () => {
          this.loadPopular(false);
        },
      );
    }
  };

  setGenre = (genresFilter?: number[]) => {
    this.setState(
      {
        filters: {
          ...this.state.filters,
          genresFilter,
        },
      },
      () => {
        this.loadPopular(false);
      },
    );
  };

  setNetworks = (networks?: NetworkType[]) => {
    // Only update and hit endpoint if there is a state change
    if (this.state.filters.networks !== networks) {
      this.setState(
        {
          filters: {
            ...this.state.filters,
            networks,
          },
        },
        () => {
          this.loadPopular(false);
        },
      );
    }
  };

  setSortOrder = (sortOrder: ListSortOptions) => {
    if (this.state.filters.sortOrder !== sortOrder) {
      this.setState(
        {
          filters: {
            ...this.state.filters,
            sortOrder,
          },
        },
        () => {
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
    const genreName = (genreItem && genreItem.name) || '';

    return genreName;
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
    const { mainItemIndex, totalLoadedImages } = this.state;
    const { loading, popular, width } = this.props;
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const mainItem = mainItemIndex === -1 ? 0 : 1;
    const totalFetchedItems = (popular && popular.length - mainItem) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const loadMore = totalNonLoadedImages <= numColumns;

    if (!loading && loadMore) {
      this.debounceLoadMore();
    }
  };

  setFilters = (filterParams: FilterParams) => {
    this.setState(
      {
        filters: filterParams,
      },
      () => {
        this.loadPopular(false);
      },
    );
  };

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  renderPopular = () => {
    const { classes, genres, popular, userSelf, thingsBySlug } = this.props;
    const {
      filters: { genresFilter, itemTypes, networks, sortOrder },
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
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={this.setFilters}
            isListDynamic={false}
            filters={this.state.filters}
          />
          <IconButton
            onClick={this.toggleFilters}
            className={classes.settings}
            color={this.state.showFilter ? 'secondary' : 'inherit'}
          >
            <Tune />
            <Typography variant="srOnly">Tune</Typography>
          </IconButton>
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          handleTypeChange={this.setType}
          handleGenreChange={this.setGenre}
          handleNetworkChange={this.setNetworks}
          handleSortChange={this.setSortOrder}
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
              if (thing && index !== this.state.mainItemIndex) {
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
    const { mainItemIndex } = this.state;
    const { popular, thingsBySlug } = this.props;

    return popular ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        <Featured featuredItem={thingsBySlug[popular[mainItemIndex]]} />
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
      withRouter(
        connect(
          mapStateToProps,
          mapDispatchToProps,
        )(Popular),
      ),
    ),
  ),
);
