import {
  CardMedia,
  createStyles,
  Fab,
  LinearProgress,
  Theme,
  Tooltip,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { Check, List as ListIcon } from '@material-ui/icons';
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

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      contain: 'strict',
      position: 'absolute',
      width: '100%',
      height: '100%',
      overflow: 'hidden',
      display: 'flex',
      // focus: blur(0.6),
      zIndex: 1,
    },
    heroContent: {
      maxWidth: 600,
      margin: '0 auto',
      padding: `${theme.spacing(8)}px 0 ${theme.spacing(7)}px`,
    },
    imageContainer: {
      width: 250,
      display: 'flex',
      flex: '0 1 auto',
      position: 'relative',
    },
    itemInformationContainer: {
      width: 250,
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#000',
      flexDirection: 'column',
      marginLeft: 20,
      position: 'relative',
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
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
}

class ItemDetails extends Component<Props, State> {
  state: State = {
    currentId: '',
    currentItemType: '',
    manageTrackingModalOpen: false,
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

  renderDescriptiveDetails = (thing: Thing) => {
    const title = getMetadataPath(thing, 'title') || '';
    const overview = getMetadataPath(thing, 'overview') || '';
    const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
    const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;

    return (
      <div className={this.props.classes.descriptionContainer}>
        <div
          style={{
            display: 'flex',
            marginBottom: 8,
            flexDirection: 'column',
            alignItems: 'self-start',
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
        <div>
          <Typography color="inherit">{overview}</Typography>
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
          <span key={lowestCostAv.id}>
            <Typography display="inline">
              <img
                key={lowestCostAv.id}
                src={logoUri}
                style={{ width: 50, borderRadius: 10 }}
              />
              {lowestCostAv.cost}
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
      <div>
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
      <div>
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

  renderItemDetails = () => {
    let { isFetching, itemDetail, userSelf } = this.props;
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
              opacity: 0.4,
            }}
          />
          <div
            style={{
              margin: 20,
              display: 'flex',
              flex: '1 1 auto',
              color: '#000',
            }}
          >
            <div style={{ display: 'flex', flexDirection: 'column' }}>
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
                {availabilities.subscription ? (
                  <Typography component="div" color="inherit">
                    Stream:
                    <div>
                      {this.renderOfferDetails(availabilities.subscription)}
                    </div>
                  </Typography>
                ) : null}

                {availabilities.rent ? (
                  <Typography component="div">
                    Rent:
                    <div>{this.renderOfferDetails(availabilities.rent)}</div>
                  </Typography>
                ) : null}

                {availabilities.buy ? (
                  <Typography component="div">
                    Buy:
                    <div>{this.renderOfferDetails(availabilities.buy)}</div>
                  </Typography>
                ) : null}
              </div>
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

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => OwnProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isFetching: appState.itemDetail.fetching,
    itemDetail: appState.itemDetail.thingsById[props.match.params.id],
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
