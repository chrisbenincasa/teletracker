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
import withUser, { WithUserProps } from '../components/withUser';
import Thing from '../types/Thing';
import { Genre as GenreModel } from '../types';
import { GenreInitiatedActionPayload } from '../actions/popular/genre';
import Featured from '../components/Featured';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';

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
  type?: 'movie' | 'show';
}

class Genre extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
  };

  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(location.search);
    let type;
    let param = params.get('type');
    if (param === 'movie' || param === 'show') {
      type = param;
    }

    this.state = {
      ...this.state,
      type,
    };
  }

  componentDidMount() {
    this.props.retrieveGenre({
      genre: this.props.match.params.id,
      thingRestrict: this.state.type,
    });

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
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
      const popularItem = genre.findIndex(
        name => name === highestRated[randomItem],
      );

      this.setState({
        mainItemIndex: popularItem,
      });
    }

    if (prevProps.location.search !== this.props.location.search) {
      let params = new URLSearchParams(location.search);
      let type;
      let param = params.get('type');
      if (param === 'movie' || param === 'show' || !param) {
        type = param;
      }

      this.setState({
        type,
      });
      this.props.retrieveGenre({
        genre: this.props.match.params.id,
        thingRestrict: type,
      });
    }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
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
            {type ? capitalize(type) + 's' : 'Content'}
          </Typography>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by All, Movies, or just TV Shows"
          >
            <Button
              color={!type ? 'secondary' : 'primary'}
              component={RouterLink}
              to={{
                pathname: location.pathname,
                search: '',
              }}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={type === 'movie' ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=movie'}
              className={classes.filterButtons}
            >
              Movies
            </Button>
            <Button
              color={type === 'show' ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=show'}
              className={classes.filterButtons}
            >
              TV
            </Button>
          </ButtonGroup>
        </div>
        <Grid container spacing={2}>
          {genre.map((result, index) => {
            let thing = thingsBySlug[result];

            if (thing && index !== this.state.mainItemIndex) {
              return <ItemCard key={result} userSelf={userSelf} item={thing} />;
            } else {
              return null;
            }
          })}
        </Grid>
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
