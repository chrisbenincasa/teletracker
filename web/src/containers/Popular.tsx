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
import { getGenreFromUrlParam } from '../components/Filters/GenreSelect';
import { getNetworkTypeFromUrlParam } from '../components/Filters/NetworkSelect';
import { getSortFromUrlParam } from '../components/Filters/SortDropdown';
import { getTypeFromUrlParam } from '../components/Filters/TypeToggle';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants';
import { AppState } from '../reducers';
import { Genre, ItemTypes, ListSortOptions, NetworkTypes } from '../types';
import { Item } from '../types/v2/Item';

const limit = 20;

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
  genresFilter?: number[];
  mainItemIndex: number;
  networks?: NetworkTypes[];
  showFilter: boolean;
  sortOrder: ListSortOptions;
  itemTypes?: ItemTypes[];
}

class Popular extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      genresFilter: getGenreFromUrlParam(),
      mainItemIndex: -1,
      networks: getNetworkTypeFromUrlParam(),
      showFilter: false,
      sortOrder: getSortFromUrlParam(),
      itemTypes: getTypeFromUrlParam(),
    };
  }

  loadPopular(passBookmark: boolean) {
    // To do: add support for sorting
    this.props.retrievePopular({
      itemTypes: this.state.itemTypes,
      networks: this.state.networks,
      limit,
      bookmark: passBookmark ? this.props.bookmark : undefined,
    });
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.loadPopular(false);

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }
  }

  componentDidUpdate(prevProps, prevState) {
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
  }

  setType = (itemTypes?: ItemTypes[]) => {
    // Only update and hit endpoint if there is a state change
    if (this.state.itemTypes !== itemTypes) {
      this.setState(
        {
          itemTypes,
        },
        () => {
          this.loadPopular(false);
        },
      );
    }
  };

  setGenre = (genres?: number[]) => {
    console.log('works!');
    console.log({ genres });
    this.setState(
      {
        genresFilter: genres,
      },
      () => {
        this.loadPopular(false);
      },
    );
  };

  setNetworks = (networks?: NetworkTypes[]) => {
    // Only update and hit endpoint if there is a state change
    if (this.state.networks !== networks) {
      this.setState(
        {
          networks,
        },
        () => {
          this.loadPopular(false);
        },
      );
    }
  };

  setSortOrder = (sortOrder: ListSortOptions) => {
    if (this.state.sortOrder !== sortOrder) {
      this.setState(
        {
          sortOrder,
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

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderLoadingCircle() {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: 200,
          height: '100%',
        }}
      >
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
    if (!this.props.loading) {
      this.debounceLoadMore();
    }
  };

  setFilters = (
    sortOrder: ListSortOptions,
    networks?: NetworkTypes[],
    itemTypes?: ItemTypes[],
    genres?: number[],
  ) => {
    this.setState(
      {
        networks,
        itemTypes,
        sortOrder,
        genresFilter: genres,
      },
      () => {
        this.loadPopular(true);
      },
    );
  };

  renderPopular = () => {
    const { classes, genres, popular, userSelf, thingsBySlug } = this.props;
    const { genresFilter, itemTypes, networks, sortOrder } = this.state;

    return popular && popular && popular.length ? (
      <div
        style={{
          padding: 8,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
          }}
        >
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(this.props.width) ? 'h6' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Popular ${
              itemTypes
                ? itemTypes.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            marginBottom: 8,
            justifyContent: 'flex-end',
            alignItems: 'center',
          }}
        >
          <ActiveFilters
            genres={genres}
            updateFilters={this.setFilters}
            genresFilter={genresFilter}
            itemTypes={itemTypes}
            networks={networks}
            sortOrder={sortOrder}
            isListDynamic={false}
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
          threshold={400}
        >
          <Grid container spacing={2}>
            {popular.map((result, index) => {
              let thing = thingsBySlug[result];
              if (thing && index !== this.state.mainItemIndex) {
                return (
                  <ItemCard key={result} userSelf={userSelf} item={thing} />
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
