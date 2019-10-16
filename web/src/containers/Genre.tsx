import {
  ButtonGroup,
  Button,
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators } from 'redux';
import * as R from 'ramda';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { retrieveGenre } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import TypeToggle, { getType } from '../components/Filters/TypeToggle';
import withUser, { WithUserProps } from '../components/withUser';
import Thing from '../types/Thing';
import { Genre as GenreModel, ItemTypes } from '../types';
import { GenreInitiatedActionPayload } from '../actions/popular/genre';
import Featured from '../components/Featured';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';

const limit = 20;

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
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
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  genre?: string[];
  genres?: GenreModel[];
  thingsBySlug: { [key: string]: Thing };
  bookmark?: string;
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
  mainItemIndex: number;
  type?: ItemTypes;
}

class Genre extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
  };

  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      type: getType(),
    };
  }

  loadGenres(passBookmark: boolean) {
    this.props.retrieveGenre({
      genre: this.props.match.params.id,
      thingRestrict: this.state.type,
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
        const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
        const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
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

  setType = (type: ItemTypes) => {
    this.setState(
      {
        type,
      },
      () => {
        this.loadGenres(false);
      },
    );
  };

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

  renderItems = () => {
    const {
      classes,
      genre,
      userSelf,
      thingsBySlug,
      genres,
      location,
      match,
    } = this.props;
    const { type } = this.state;
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
            {type && type.includes('movie')
              ? capitalize(type[0]) + 's'
              : 'Content'}
          </Typography>
          <div
            style={{ display: 'flex', flexDirection: 'row', marginBottom: 10 }}
          >
            <TypeToggle handleChange={this.setType} />
          </div>
        </div>

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
