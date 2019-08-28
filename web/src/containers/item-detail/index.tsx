import {
  Avatar,
  Button,
  CardContent,
  CardMedia,
  Chip,
  Collapse,
  createStyles,
  Fab,
  Grid,
  Hidden,
  IconButton,
  LinearProgress,
  Popover,
  Tabs,
  Tab,
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
} from '../../actions/item-detail';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../../actions/user';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { ActionType, Availability, Network, Thing } from '../../types';
import { getMetadataPath } from '../../utils/metadata-access';
import { ResponsiveImage } from '../../components/ResponsiveImage';
import ThingAvailability from '../../components/Availability';
import Cast from '../../components/Cast';
import imagePlaceholder from '../../assets/images/imagePlaceholder.png';
import AddToListDialog from '../../components/AddToListDialog';
import { parseInitials } from '../../utils/textHelper';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
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

    modal: {
      backgroundColor: fade(theme.palette.background.paper, 0.75),
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

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

type NotOwnProps = RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  currentId: string;
  currentItemType: string;
  manageTrackingModalOpen: boolean;

  showPlayIcon: boolean;
  trailerModalOpen: boolean;
  anchorEl: any;
}

class ItemDetails extends Component<Props, State> {
  state: State = {
    currentId: '',
    currentItemType: '',
    manageTrackingModalOpen: false,

    showPlayIcon: false,
    trailerModalOpen: false,
    anchorEl: null,
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

  togglePosterHover = () => {
    this.setState({ showPlayIcon: !this.state.showPlayIcon });
  };

  toggleTrailerModalOpen = event => {
    this.setState({ trailerModalOpen: true, anchorEl: event.currentTarget });
  };

  toggleTrailerModalClose = () => {
    this.setState({ trailerModalOpen: false, anchorEl: null });
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
          <Typography color="inherit" variant="body1">
            {`(${voteCount})`}
          </Typography>
        </div>
      </div>
    );
  };

  renderDescriptiveDetails = (thing: Thing) => {
    const overview = getMetadataPath(thing, 'overview') || '';
    const genres = Object(getMetadataPath(thing, 'genres')) || [];

    return (
      <div className={this.props.classes.descriptionContainer}>
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
        <div style={{ display: 'flex' }}>
          {genres &&
            genres.length &&
            genres.map(genre => (
              <Chip label={`${genre.name}`} style={{ margin: 5 }} />
            ))}
        </div>
      </div>
    );
  };

  renderWatchedToggle = () => {
    let watchedStatus = this.itemMarkedAsWatched();
    let watchedCTA = watchedStatus ? 'Mark as unwatched' : 'Mark as watched';
    return (
      <div className={this.props.classes.itemCTA}>
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
    let trackingCTA = 'Manage Tracking';
    return (
      <div className={this.props.classes.itemCTA}>
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

    if (!itemDetail) {
      return this.renderLoading();
    }

    let availabilities: { [key: string]: Availability[] };

    if (itemDetail.availability) {
      availabilities = R.mapObjIndexed(
        R.pipe(
          R.filter<Availability, 'array'>(R.propEq('isAvailable', true)),
          R.sortBy(R.prop('cost')),
        ),
        R.groupBy(R.prop('offerType'), itemDetail.availability),
      );
    } else {
      availabilities = {};
    }

    console.log(this.props);

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
                onMouseEnter={this.togglePosterHover}
                onMouseLeave={this.togglePosterHover}
              >
                {this.state.showPlayIcon &&
                itemDetail.id === '7b6dbeb1-8353-45a7-8c9b-7f9ab8b037f8' ? (
                  <IconButton
                    aria-haspopup="true"
                    color="inherit"
                    style={{ position: 'absolute' }}
                    onClick={this.toggleTrailerModalOpen}
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

        <Popover
          anchorOrigin={{
            vertical: 'top',
            horizontal: 'left',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'left',
          }}
          anchorEl={this.state.anchorEl}
          open={this.state.trailerModalOpen}
          onClose={this.toggleTrailerModalClose}
          className={this.props.classes.modal}
        >
          <iframe
            width="615"
            height="370"
            src="https://www.youtube.com/embed/m8e-FF8MsqU"
            frameBorder="0"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        </Popover>
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
