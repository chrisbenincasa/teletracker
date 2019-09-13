import {
  ButtonGroup,
  Button,
  CardMedia,
  createStyles,
  Fab,
  Fade,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import { fade } from '@material-ui/core/styles/colorManipulator';
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
import { ResponsiveImage } from '../components/ResponsiveImage';
import AddToListDialog from '../components/AddToListDialog';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import Thing from '../types/Thing';
import { Genre as GenreModel } from '../types';
import { GenreInitiatedActionPayload } from '../actions/popular/genre';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 230,
      },
      width: 115,
      display: 'flex',
      flex: '0 1 auto',
      flexDirection: 'column',
      position: 'absolute',
      top: 20,
      left: 20,
    },
    posterImage: {
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
    },
    title: {
      [theme.breakpoints.up('sm')]: {
        fontSize: '3em',
      },
      fontSize: '1em',
      fontWeight: 700,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      position: 'absolute',
      bottom: 0,
      right: 8,
      marginBottom: 8,
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
    itemCTA: {
      [theme.breakpoints.down('sm')]: {
        display: 'none',
      },
      width: '100%',
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
  manageTrackingModalOpen: boolean;
  type?: 'movie' | 'show';
}

class Genre extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
    manageTrackingModalOpen: false,
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

  renderTitle = (thing: Thing) => {
    const { classes } = this.props;
    const title = thing.name || '';

    return (
      <div className={classes.titleContainer} style={{}}>
        <Typography color="inherit" variant="h3" className={classes.title}>
          {`${title}`}
        </Typography>
      </div>
    );
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    const trackingCTA = 'Add to List';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.openManageTrackingModal}
          style={{ marginTop: 5, width: '100%' }}
        >
          <ListIcon style={{ marginRight: 8 }} />
          {trackingCTA}
        </Fab>
      </div>
    );
  };

  renderMainPopularItem = () => {
    let { classes, genre, thingsBySlug, userSelf } = this.props;
    const { mainItemIndex, manageTrackingModalOpen } = this.state;
    genre = genre || [];
    const thing = thingsBySlug[genre[mainItemIndex]];

    return thing ? (
      <Fade in={true}>
        <div style={{ position: 'relative' }}>
          <ResponsiveImage
            item={thing}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              width: '100%',
              height: '100%',
              maxHeight: 424,
              boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
            }}
            pictureStyle={{
              display: 'block',
            }}
          />
          <div className={classes.posterContainer}>
            <RouterLink
              to={'/' + thing.type + '/' + thing.normalizedName}
              style={{
                display: 'block',
                height: '100%',
                textDecoration: 'none',
              }}
            >
              <CardMedia
                src={imagePlaceholder}
                item={thing}
                component={ResponsiveImage}
                imageType="poster"
                imageStyle={{
                  width: '100%',
                }}
                pictureStyle={{
                  display: 'block',
                }}
              />
            </RouterLink>

            {this.renderTrackingToggle()}
            <AddToListDialog
              open={manageTrackingModalOpen}
              onClose={this.closeManageTrackingModal.bind(this)}
              userSelf={userSelf!}
              item={thing}
            />
          </div>
          {this.renderTitle(thing)}
        </div>
      </Fade>
    ) : null;
  };

  renderPopular = () => {
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
    return this.props.genre && this.props.genres ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        {this.renderMainPopularItem()}
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
