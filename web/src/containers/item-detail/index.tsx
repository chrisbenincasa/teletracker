import {
  Avatar,
  CardContent,
  CardMedia,
  Chip,
  Collapse,
  createStyles,
  Fab,
  Grid,
  Hidden,
  LinearProgress,
  Tabs,
  Tab,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import {
  Check,
  List as ListIcon,
  Subscriptions,
  AttachMoney,
  Cloud,
} from '@material-ui/icons';
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
      position: 'absolute',
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
    imageContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
      width: '80%',
      display: 'flex',
      flex: '0 1 auto',
      position: 'relative',
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
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      marginLeft: 20,
      position: 'relative',
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: 10,
    },
    availabilePlatforms: {
      display: 'flex',
      justifyContent: 'flex-start',
      flexWrap: 'wrap',
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
  openTab: number;
}

class ItemDetails extends Component<Props, State> {
  state: State = {
    currentId: '',
    currentItemType: '',
    manageTrackingModalOpen: false,
    openTab: 0,
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

  manageAvailabilityTabs = value => {
    this.setState({ openTab: value });
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
            genres.map(genre => (
              <Chip label={`${genre.name}`} style={{ margin: 5 }} />
            ))}
        </div>
      </div>
    );
  };

  renderOfferDetails = (availabilities: Availability[]) => {
    let preferences = this.props.userSelf.preferences;
    let networkSubscriptions = this.props.userSelf.networks;
    let onlyShowsSubs = preferences.showOnlyNetworkSubscriptions;

    const includeFromPrefs = (av: Availability, network: Network) => {
      let showFromSubscriptions = onlyShowsSubs
        ? R.any(R.propEq('slug', network.slug), networkSubscriptions)
        : true;

      let hasPresentation = av.presentationType
        ? R.contains(av.presentationType, preferences.presentationTypes)
        : true;

      return showFromSubscriptions && hasPresentation;
    };

    const availabilityFilter = (av: Availability) => {
      return !R.isNil(av.network) && includeFromPrefs(av, av.network!);
    };

    let groupedByNetwork = availabilities
      ? R.groupBy(
          (av: Availability) => av.network!.slug,
          R.filter(availabilityFilter, availabilities),
        )
      : {};

    return R.values(
      R.mapObjIndexed(avs => {
        let lowestCostAv = R.head(R.sortBy(R.prop('cost'))(avs))!;
        let hasHd = R.find(R.propEq('presentationType', 'hd'), avs);
        let has4k = R.find(R.propEq('presentationType', '4k'), avs);

        let logoUri =
          '/images/logos/' + lowestCostAv.network!.slug + '/icon.jpg';

        return (
          <span key={lowestCostAv.id} style={{ margin: 10 }}>
            <Typography
              display="inline"
              style={{ display: 'flex', flexDirection: 'column' }}
            >
              <img
                key={lowestCostAv.id}
                src={logoUri}
                style={{ width: 50, borderRadius: 10 }}
              />
              {lowestCostAv.cost && (
                <Chip
                  size="small"
                  label={`$${lowestCostAv.cost}`}
                  style={{ marginTop: 5 }}
                />
              )}
            </Typography>
          </span>
        );
      }, groupedByNetwork),
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

  renderCastDetails = (thing: Thing) => {
    const credits = Object(getMetadataPath(thing, 'credits'));

    return credits && credits.cast && credits.cast.length > 0 ? (
      <React.Fragment>
        <div style={{ marginTop: 10 }}>
          <Typography color="inherit" variant="h5">
            Cast
          </Typography>

          <Grid container>
            {credits.cast.map(person => (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  maxWidth: 100,
                  margin: 10,
                }}
              >
                <Avatar
                  alt={person.name}
                  src={
                    person.profile_path
                      ? `https://image.tmdb.org/t/p/w185/${person.profile_path}`
                      : ''
                  }
                  style={{ width: 100, height: 100 }}
                >
                  {person.profile_path
                    ? null
                    : parseInitials(person.name, 'name')}
                </Avatar>
                <Typography
                  variant="subtitle1"
                  color="inherit"
                  style={{ fontWeight: 'bold' }}
                  align="center"
                >
                  {person.character}
                </Typography>
                <Typography
                  variant="subtitle2"
                  color="inherit"
                  style={{ fontStyle: 'Italic' }}
                  align="center"
                >
                  {person.name}
                </Typography>
              </div>
            ))}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
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
    let { isFetching, itemDetail, userSelf } = this.props;
    let { manageTrackingModalOpen, openTab } = this.state;

    if (!itemDetail) {
      return this.renderLoading();
    }
    console.log(itemDetail);

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

    return isFetching || !itemDetail ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <div className={this.props.classes.backdrop}>
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
              height: '100%',
              opacity: 0.2,
              filter: 'blur(3px)',
            }}
          />
          <div className={this.props.classes.itemDetailContainer}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
            >
              <Hidden smUp>{this.renderTitle(itemDetail)}</Hidden>

              <div className={this.props.classes.imageContainer}>
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
            <div className={this.props.classes.itemInformationContainer}>
              {this.renderDescriptiveDetails(itemDetail)}
              <div>
                <Tabs
                  value={this.state.openTab}
                  indicatorColor="primary"
                  textColor="primary"
                  aria-label="icon label tabs example"
                  variant="fullWidth"
                >
                  <Tab
                    icon={<Subscriptions />}
                    label="Stream"
                    onClick={() => this.manageAvailabilityTabs(0)}
                  />
                  <Tab
                    icon={<Cloud />}
                    label="Rent"
                    onClick={() => this.manageAvailabilityTabs(1)}
                  />
                  <Tab
                    icon={<AttachMoney />}
                    label="Buy"
                    onClick={() => this.manageAvailabilityTabs(2)}
                  />
                </Tabs>
                {/* <Paper square className={this.props.classes.root}> */}
                <Collapse in={openTab === 0} timeout="auto" unmountOnExit>
                  {availabilities.subscription ? (
                    <CardContent
                      className={this.props.classes.availabilePlatforms}
                    >
                      {this.renderOfferDetails(availabilities.subscription)}
                    </CardContent>
                  ) : null}
                </Collapse>
                <Collapse in={openTab === 1} timeout="auto" unmountOnExit>
                  {availabilities.rent ? (
                    <CardContent
                      className={this.props.classes.availabilePlatforms}
                    >
                      {this.renderOfferDetails(availabilities.rent)}
                    </CardContent>
                  ) : null}
                </Collapse>
                <Collapse in={openTab === 2} timeout="auto" unmountOnExit>
                  {availabilities.buy ? (
                    <CardContent
                      className={this.props.classes.availabilePlatforms}
                    >
                      {this.renderOfferDetails(availabilities.buy)}
                    </CardContent>
                  ) : null}
                </Collapse>
                {/* </Paper> */}
              </div>
              {this.renderCastDetails(itemDetail)}
              {this.renderSeriesDetails(itemDetail)}
            </div>
          </div>
        </div>
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
