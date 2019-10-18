import {
  createStyles,
  Grid,
  IconButton,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { Tune } from '@material-ui/icons';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import * as R from 'ramda';
import { AppState } from '../reducers';
import { retrieveGenre } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import { getTypeFromUrlParam } from '../components/Filters/TypeToggle';
import { getNetworkTypeFromUrlParam } from '../components/Filters/NetworkSelect';
import { getSortFromUrlParam } from '../components/Filters/SortDropdown';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import withUser, { WithUserProps } from '../components/withUser';
import Thing from '../types/Thing';
import {
  Genre as GenreModel,
  ItemTypes,
  ListSortOptions,
  NetworkTypes,
} from '../types';
import { GenreInitiatedActionPayload } from '../actions/popular/genre';
import Featured from '../components/Featured';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { ApiItem } from '../types/v2';
import { Item } from '../types/v2/Item';

const limit = 20;

const styles = (theme: Theme) =>
  createStyles({
    title: {
      [theme.breakpoints.up('sm')]: {
        fontSize: '2.5em',
      },
      fontSize: '1.5em',
      fontWeight: 700,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
      whiteSpace: 'nowrap',
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  bookmark?: string;
  genre?: string[];
  genres?: GenreModel[];
  thingsBySlug: { [key: string]: Item };
  isAuthed: boolean;
  loading: boolean;
}

interface RouteParams {
  id: string;
}

interface DispatchProps {
  retrieveGenre: (payload: GenreInitiatedActionPayload) => void;
}

type Props = OwnProps &
  InjectedProps &
  DispatchProps &
  WithUserProps &
  RouteComponentProps<RouteParams>;

interface State {
  itemTypes?: ItemTypes[];
  mainItemIndex: number;
  networks?: NetworkTypes[];
  showFilter: boolean;
  sortOrder: ListSortOptions;
}

class Genre extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(location.search);

    this.state = {
      ...this.state,
      itemTypes: getTypeFromUrlParam(),
      mainItemIndex: -1,
      networks: getNetworkTypeFromUrlParam(),
      showFilter: Boolean(
        params.has('sort') ||
          params.has('genres') ||
          params.has('networks') ||
          params.has('types'),
      ),
      sortOrder: getSortFromUrlParam(),
    };
  }

  loadGenres(passBookmark: boolean) {
    this.props.retrieveGenre({
      genre: this.props.match.params.id,
      networks: this.state.networks,
      thingRestrict: this.state.itemTypes,
      bookmark: passBookmark ? this.props.bookmark : undefined,
      limit,
    });
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.loadGenres(false);

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }
  }

  componentDidUpdate(prevProps: Props) {
    const { genre, thingsBySlug } = this.props;
    const { mainItemIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if ((!prevProps.genre && genre) || (genre && mainItemIndex === -1)) {
      const highestRated = genre.filter(item => {
        const thing = thingsBySlug[item];
        // TODO make better
        const voteAverage =
          thing.ratings && thing.ratings.length
            ? thing.ratings[0].vote_average
            : 0;
        const voteCount =
          thing.ratings && thing.ratings.length
            ? thing.ratings[0].vote_count || 0
            : 0;
        return voteAverage > 7 && voteCount > 1000;
      });

      const randomItem = Math.floor(Math.random() * highestRated.length);
      if (randomItem === 0) {
        this.setState({
          mainItemIndex: 0,
        });
      } else {
        const popularItem = genre.findIndex(
          name => name === highestRated[randomItem],
        );

        this.setState({
          mainItemIndex: popularItem,
        });
      }
    }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  debounceLoadMore = _.debounce(() => {
    this.loadGenres(true);
  }, 250);

  loadMoreResults = () => {
    if (!this.props.loading) {
      this.debounceLoadMore();
    }
  };

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  setType = (type?: ItemTypes[]) => {
    this.setState(
      {
        itemTypes: type,
      },
      () => {
        this.loadGenres(false);
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
          this.loadGenres(false);
        },
      );
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
      },
      () => {
        this.loadGenres(false);
      },
    );
  };

  setSortOrder = (sortOrder: ListSortOptions) => {
    if (this.state.sortOrder !== sortOrder) {
      this.setState(
        {
          sortOrder,
        },
        () => {
          this.loadGenres(false);
        },
      );
    }
  };

  renderItems = () => {
    const {
      classes,
      genre,
      userSelf,
      thingsBySlug,
      genres,
      match,
    } = this.props;
    const { itemTypes, networks, sortOrder } = this.state;
    const genreModel = R.find(g => g.slug === match.params.id, genres!)!;

    const capitalize = (s: string) => {
      return s.charAt(0).toUpperCase() + s.slice(1);
    };

    return genre && genre && genre.length ? (
      <div style={{ padding: 8, margin: 20 }}>
        <div
          style={{ display: 'flex', flexDirection: 'row', marginBottom: 10 }}
        >
          <Typography
            color="inherit"
            variant="h4"
            style={{ flexGrow: 1 }}
            className={classes.title}
          >
            Popular {genreModel.name}{' '}
            {itemTypes && itemTypes.includes('movie')
              ? capitalize(itemTypes[0]) + 's'
              : 'Content'}
          </Typography>
          <ActiveFilters
            updateFilters={this.setFilters}
            itemTypes={itemTypes}
            networks={networks}
            sortOrder={sortOrder}
            listIsDynamic={false}
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
          handleNetworkChange={this.setNetworks}
          handleSortChange={this.setSortOrder}
          isListDynamic={false}
          showGenre={false}
        />
        <InfiniteScroll
          pageStart={0}
          loadMore={() => this.loadMoreResults()}
          hasMore={Boolean(this.props.bookmark)}
          useWindow
          threshold={400}
        >
          <Grid container spacing={2}>
            {genre.map((result, index) => {
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
        </InfiniteScroll>
      </div>
    ) : null;
  };

  render() {
    const { mainItemIndex } = this.state;
    const { genre, genres, thingsBySlug } = this.props;

    return genre && genres ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        <Featured featuredItem={thingsBySlug[genre[mainItemIndex]]} />
        {this.renderItems()}
      </div>
    ) : (
      this.renderLoading()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    genre: appState.popular.genre,
    genres: appState.metadata.genres,
    thingsBySlug: appState.itemDetail.thingsBySlug,
    loading: appState.popular.loadingGenres,
    bookmark: appState.popular.genreBookmark,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrieveGenre,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Genre),
    ),
  ),
);
