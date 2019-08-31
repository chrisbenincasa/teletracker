import {
  Backdrop,
  CardMedia,
  Chip,
  createStyles,
  Fab,
  Fade,
  Grid,
  Hidden,
  IconButton,
  LinearProgress,
  Modal,
  Popover,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { fade } from '@material-ui/core/styles/colorManipulator';
import { Check, List as ListIcon, PlayArrow } from '@material-ui/icons';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  itemFetchInitiated,
  ItemFetchInitiatedPayload,
} from '../actions/item-detail';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { ActionType, Thing } from '../types';
import { getMetadataPath } from '../utils/metadata-access';
import { ResponsiveImage } from '../components/ResponsiveImage';
import ThingAvailability from '../components/Availability';
import Cast from '../components/Cast';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import AddToListDialog from '../components/AddToListDialog';
import { formatRuntime } from '../utils/textHelper';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    trailerVideo: {
      width: '60vw',
      height: '34vw',
      [theme.breakpoints.down('sm')]: {
        width: '100vw',
        height: '56vw',
      },
    },
    modal: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    root: {
      flexGrow: 1,
    },
    card: {
      margin: '10px 0',
    },
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
      background:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
      //To do: integrate with theme styling for primary
    },
    genre: { margin: 5 },
    genreContainer: { display: 'flex' },
    itemDetailContainer: {
      margin: 20,
      display: 'flex',
      flex: '1 1 auto',
      color: '#fff',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
      },
    },
    heroContent: {
      maxWidth: 600,
      margin: '0 auto',
      padding: `${theme.spacing(8)}px 0 ${theme.spacing(7)}px`,
    },
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
      width: '80%',
      display: 'flex',
      flex: '0 1 auto',
      boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
      position: 'relative',
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
    },
    itemCTA: {
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
      width: '100%',
    },
    itemInformationContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
        marginLeft: 20,
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: 10,
    },
  });

interface OwnProps {
  isAuthed: boolean;
  isFetching: boolean;
  itemDetail?: Thing;
}

interface DispatchProps {
  fetchItemDetails: (payload: ItemFetchInitiatedPayload) => void;
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

interface RouteParams {
  id: string;
  type: string;
}

type NotOwnProps = RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

type Props = OwnProps & NotOwnProps;

interface State {
  currentId: string;
  currentItemType: string;
  manageTrackingModalOpen: boolean;
  showPlayIcon: boolean;
  trailerModalOpen: boolean;
}

class ItemDetails extends Component<Props, State> {
  state: State = {
    currentId: '',
    currentItemType: '',
    manageTrackingModalOpen: false,
    showPlayIcon: false,
    trailerModalOpen: false,
  };

  componentDidMount() {
    let { match } = this.props;
    let itemId = match.params.id;
    let itemType = match.params.type;

    this.setState({
      currentId: itemId,
      currentItemType: itemType,
    });

    this.props.fetchItemDetails({ id: itemId, type: itemType });
  }

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  showPlayTrailerIcon = () => {
    this.setState({ showPlayIcon: true });
  };

  hidePlayTrailerIcon = () => {
    this.setState({ showPlayIcon: false });
  };

  openTrailerModal = () => {
    this.setState({ trailerModalOpen: true });
  };

  closeTrailerModal = () => {
    this.setState({ trailerModalOpen: false });
  };

  toggleItemWatched = () => {
    let payload = {
      thingId: this.state.currentId,
      action: ActionType.Watched,
    };

    if (this.itemMarkedAsWatched()) {
      this.props.removeUserItemTags(payload);
    } else {
      this.props.updateUserItemTags(payload);
    }
  };

  itemMarkedAsWatched = () => {
    if (this.props.itemDetail && this.props.itemDetail.userMetadata) {
      return R.any(tag => {
        return tag.action == ActionType.Watched;
      }, this.props.itemDetail.userMetadata.tags);
    }

    return false;
  };

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderTitle = (thing: Thing) => {
    const title =
      (thing.type === 'movie'
        ? getMetadataPath(thing, 'title')
        : getMetadataPath(thing, 'name')) || '';
    const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
    const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
    const runtime =
      thing.type === 'movie'
        ? formatRuntime(getMetadataPath(thing, 'runtime'), thing.type)
        : formatRuntime(getMetadataPath(thing, 'episode_run_time'), thing.type);

    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          width: '80%',
          marginBottom: 10,
        }}
      >
        <Typography color="inherit" variant="h4">
          {`${title}`}
        </Typography>
        <div style={{ display: 'flex', flexDirection: 'row' }}>
          <Rating value={voteAverage / 2} precision={0.1} readOnly />
          <Typography
            color="inherit"
            variant="body1"
            style={{ marginRight: 10 }}
          >
            {`(${voteCount})`}
          </Typography>
          <Typography
            color="inherit"
            variant="body1"
            style={{ marginLeft: 10 }}
          >
            {`${runtime}`}
          </Typography>
        </div>
      </div>
    );
  };

  renderDescriptiveDetails = (thing: Thing) => {
    const { classes } = this.props;
    const overview = getMetadataPath(thing, 'overview') || '';
    const genres = Object(getMetadataPath(thing, 'genres')) || [];

    return (
      <div className={classes.descriptionContainer}>
        <div
          style={{
            display: 'flex',
            marginBottom: 8,
            flexDirection: 'column',
            alignItems: 'self-start',
            color: '#fff',
          }}
        >
          <Hidden only={['xs', 'sm']}>{this.renderTitle(thing)}</Hidden>
        </div>
        <div>
          <Typography color="inherit">{overview}</Typography>
        </div>
        <div className={classes.genreContainer}>
          {genres &&
            genres.length &&
            genres.map(genre => (
              <Chip
                key={genre.id}
                label={genre.name}
                className={classes.genre}
                onClick={() => {}}
              />
            ))}
        </div>
      </div>
    );
  };

  renderWatchedToggle = () => {
    const { classes } = this.props;
    let watchedStatus = this.itemMarkedAsWatched();
    let watchedCTA = watchedStatus ? 'Mark as unwatched' : 'Mark as watched';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.toggleItemWatched}
          style={{ marginTop: 5, width: '100%' }}
          color={watchedStatus ? 'primary' : undefined}
        >
          <Check style={{ marginRight: 8 }} />
          {watchedCTA}
        </Fab>
      </div>
    );
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    let trackingCTA = 'Manage Tracking';

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

  renderSeriesDetails = (thing: Thing) => {
    const seasons = Object(getMetadataPath(thing, 'seasons'));

    return seasons && seasons.length > 0 ? (
      <React.Fragment>
        <Typography color="inherit" variant="h5">
          Seasons
        </Typography>

        <Grid container>
          {seasons.map(season =>
            season.episode_count > 0 && season.poster_path ? (
              <img
                src={`https://image.tmdb.org/t/p/w342/${season.poster_path}`}
                style={{ margin: 10, width: 100 }}
              />
            ) : null,
          )}
        </Grid>
      </React.Fragment>
    ) : null;
  };

  renderItemDetails = () => {
    let { classes, isFetching, itemDetail, userSelf } = this.props;
    let { manageTrackingModalOpen } = this.state;

    console.log(itemDetail);

    return isFetching || !itemDetail ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <div className={classes.backdrop}>
          <ResponsiveImage
            item={itemDetail}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              objectPosition: 'center top',
              width: '100%',
              height: '100%',
            }}
            pictureStyle={{
              position: 'absolute',
              width: '100%',
              height: 'auto',
              opacity: 0.2,
              filter: 'blur(3px)',
            }}
          />
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          />
          <div className={classes.itemDetailContainer}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
            >
              <Hidden smUp>{this.renderTitle(itemDetail)}</Hidden>
              <div
                className={classes.posterContainer}
                onMouseEnter={this.showPlayTrailerIcon}
                onMouseLeave={this.hidePlayTrailerIcon}
              >
                {this.state.showPlayIcon &&
                itemDetail.id === '7b6dbeb1-8353-45a7-8c9b-7f9ab8b037f8' ? (
                  <IconButton
                    aria-haspopup="true"
                    color="inherit"
                    style={{ position: 'absolute' }}
                    onClick={this.openTrailerModal}
                  >
                    <PlayArrow fontSize="large" />
                  </IconButton>
                ) : null}
                <CardMedia
                  src={imagePlaceholder}
                  item={itemDetail}
                  component={ResponsiveImage}
                  imageType="poster"
                  imageStyle={{
                    width: '100%',
                  }}
                />
              </div>

              {this.renderWatchedToggle()}
              {this.renderTrackingToggle()}
              <AddToListDialog
                open={manageTrackingModalOpen}
                onClose={this.closeManageTrackingModal.bind(this)}
                userSelf={userSelf!}
                item={itemDetail}
              />
            </div>
            <div className={classes.itemInformationContainer}>
              {this.renderDescriptiveDetails(itemDetail)}
              <div>
                <div style={{ marginTop: 10 }}>
                  <ThingAvailability
                    userSelf={userSelf!}
                    itemDetail={itemDetail}
                  />
                </div>
              </div>
              <Cast itemDetail={itemDetail} />
              {this.renderSeriesDetails(itemDetail)}
            </div>
          </div>
        </div>
        <Modal
          aria-labelledby="transition-modal-title"
          aria-describedby="transition-modal-description"
          className={classes.modal}
          open={this.state.trailerModalOpen}
          onClose={this.closeTrailerModal}
          closeAfterTransition
          BackdropComponent={Backdrop}
          BackdropProps={{
            timeout: 500,
          }}
          style={{ backgroundColor: 'rgba(0, 0, 0, 0.8)' }}
        >
          <Fade in={this.state.trailerModalOpen}>
            <iframe
              width="600"
              height="338"
              src="https://www.youtube.com/embed/m8e-FF8MsqU?autoplay=1 "
              frameBorder="0"
              allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              className={classes.trailerVideo}
            />
          </Fade>
        </Modal>
      </React.Fragment>
    );
  };

  render() {
    let { isAuthed } = this.props;

    return isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>
        {this.renderItemDetails()}
      </div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const findThingBySlug = (things: Thing[], slug: string) => {
  return R.find(t => t.normalizedName === slug, things);
};

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => OwnProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isFetching: appState.itemDetail.fetching,
    itemDetail:
      appState.itemDetail.thingsById[props.match.params.id] ||
      appState.itemDetail.thingsBySlug[props.match.params.id] ||
      findThingBySlug(
        R.values(appState.itemDetail.thingsById),
        props.match.params.id,
      ),
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      fetchItemDetails: itemFetchInitiated,
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(ItemDetails),
    ),
  ),
);
