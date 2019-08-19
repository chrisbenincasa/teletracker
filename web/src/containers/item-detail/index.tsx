import {
  CardMedia,
  CircularProgress,
  createStyles,
  Fab,
  LinearProgress,
  Paper,
  Theme,
  Tooltip,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Check } from '@material-ui/icons';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {itemFetchInitiated, ItemFetchInitiatedPayload} from '../../actions/item-detail';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../../actions/user';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { ActionType, Availability, Network, Thing } from '../../types';
import {
  getBackdropUrl,
  getOverviewPath,
  getPosterPath,
  getTitlePath,
  getVoteAveragePath,
} from '../../utils/metadata-access';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      backgroundSize: 'cover',
      width: '100%',
    },
    backdropImage: {
      backgroundSize: 'cover',
      backgroundPosition: '50% 50%',
      padding: '3em 0',
      display: 'flex',
    },
    heroContent: {
      maxWidth: 600,
      margin: '0 auto',
      padding: `${theme.spacing(8)}px 0 ${theme.spacing(7)}px`,
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    imageContainer: {
      width: 250,
      display: 'flex',
      flex: '0 1 auto',
      marginLeft: 20,
    },
    itemInformationContainer: {
      width: 250,
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: theme.palette.grey[50],
      flexDirection: 'column',
      marginLeft: 20,
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
}

const gradient =
  'radial-gradient(circle at 20% 50%, rgba(11.76%, 15.29%, 17.25%, 0.98) 0%, rgba(11.76%, 15.29%, 17.25%, 0.88) 100%)';

class ItemDetails extends Component<Props, State> {
  componentDidMount() {
    let { match } = this.props;
    let itemId = match.params.id;
    let itemType = match.params.type;

    this.setState({
      currentId: itemId,
      currentItemType: itemType,
    });

    this.props.fetchItemDetails({id: itemId, type: itemType});
  }

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

  renderPoster = (thing: Thing) => {
    let poster = getPosterPath(thing);
    if (poster) {
      return (
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w342' + poster}
          title={thing.name}
        />
      );
    } else {
      return null;
    }
  };

  renderDescriptiveDetails = (thing: Thing) => {
    let title = getTitlePath(thing) || '';
    let overview = getOverviewPath(thing) || '';
    let voteAverage = Number(getVoteAveragePath(thing)) || 0;

    return (
      <div className={this.props.classes.descriptionContainer}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
          <Typography color="inherit" variant="h4">
            {`${title} (${voteAverage * 10})`}{' '}
          </Typography>
          <CircularProgress variant="static" value={voteAverage * 10} />
          {this.renderWatchedToggle()}
        </div>
        <div>
          <Typography color="inherit">{overview}</Typography>
        </div>
      </div>
    );
  };

  backdropStyle = (item: Thing) => {
    let backdrop = getBackdropUrl(item, '780');

    return {
      backgroundImage: `${gradient}, url(${backdrop})`,
    };
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
          <span key={lowestCostAv.id} style={{ color: 'white' }}>
            <Typography display="inline" color="inherit">
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
    return (
      <div>
        <Tooltip
          title={
            this.itemMarkedAsWatched()
              ? 'Mark as not watched'
              : 'Mark as watched'
          }
          placement="top"
        >
          <Fab
            size="small"
            variant="extended"
            aria-label="Add"
            onClick={this.toggleItemWatched}
            style={{ margin: 8 }}
            color={this.itemMarkedAsWatched() ? 'primary' : undefined}
          >
            <Check style={{ marginRight: 8 }} />
            Watched
          </Fab>
        </Tooltip>
      </div>
    );
  };

  renderItemDetails = () => {
    let { isFetching, itemDetail, match, userSelf } = this.props;
    let itemId = match.params.id;
    let itemType = String(match.params.type);

    if (!itemDetail) {
      return this.renderLoading();
    }

    let backdropStyle = this.backdropStyle(itemDetail);

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
          <div
            className={this.props.classes.backdropImage}
            style={backdropStyle}
          >
            <Paper className={this.props.classes.imageContainer}>
              {this.renderPoster(itemDetail)}
            </Paper>
            <div className={this.props.classes.itemInformationContainer}>
              {this.renderDescriptiveDetails(itemDetail)}
              <div style={{ color: 'white' }}>
                {availabilities.subscription ? (
                  <Typography component="div" color="inherit">
                    Stream:
                    <div>
                      {this.renderOfferDetails(availabilities.subscription)}
                    </div>
                  </Typography>
                ) : null}

                {availabilities.rent ? (
                  <Typography component="div" color="inherit">
                    Rent:
                    <div>{this.renderOfferDetails(availabilities.rent)}</div>
                  </Typography>
                ) : null}

                {availabilities.buy ? (
                  <Typography component="div" color="inherit">
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
