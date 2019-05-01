import {
  CardMedia,
  createStyles,
  CssBaseline,
  LinearProgress,
  Paper,
  Theme,
  WithStyles,
  withStyles,
  Typography,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { fetchItemDetails } from '../../actions/item-detail';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing, Availability } from '../../types';
import { getBackdropUrl, getPosterPath } from '../../utils/metadata-access';

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
    },
    heroContent: {
      maxWidth: 600,
      margin: '0 auto',
      padding: `${theme.spacing.unit * 8}px 0 ${theme.spacing.unit * 6}px`,
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    paper: {
      width: '250px',
    },
  });

interface OwnProps {
  isAuthed: boolean;
  isFetching: boolean;
  itemDetail?: Thing;
}

interface DispatchProps {
  fetchItemDetails: (id: number, type: string) => void;
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

interface State {
  currentId: number;
}

const gradient =
  'radial-gradient(circle at 20% 50%, rgba(11.76%, 15.29%, 17.25%, 0.98) 0%, rgba(11.76%, 15.29%, 17.25%, 0.88) 100%)';

class ItemDetails extends Component<Props, State> {
  componentDidMount() {
    let { match } = this.props;
    let itemId = Number(match.params.id);
    let itemType = match.params.type;

    this.props.fetchItemDetails(itemId, itemType);
  }

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

  backdropStyle = (item: Thing) => {
    let backdrop = getBackdropUrl(item, '780');

    return {
      backgroundImage: `${gradient}, url(${backdrop})`,
    };
  };

  renderOfferDetails = (availabilities: Availability[]) => {
    let groupedByNetwork = availabilities
      ? R.groupBy(
          (av: Availability) => av.network!.slug,
          R.filter(av => !R.isNil(av.network), availabilities),
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
          <span style={{ color: 'white' }}>
            <Typography inline color="inherit">
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

  renderItemDetails = () => {
    let { itemDetail, match } = this.props;
    let itemId = Number(match.params.id);
    let itemType = String(match.params.type);

    if (!itemDetail) {
      return this.renderLoading();
    }

    let backdropStyle = this.backdropStyle(itemDetail);

    let availabilities = R.mapObjIndexed(
      R.pipe(
        R.filter<Availability, 'array'>(R.propEq('isAvailable', true)),
        R.sortBy(R.prop('cost')),
      ),
      R.groupBy(R.prop('offerType'), itemDetail.availability),
    );

    return this.props.isFetching || !itemDetail ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <CssBaseline />
        <div className={this.props.classes.backdrop}>
          <div
            className={this.props.classes.backdropImage}
            style={backdropStyle}
          >
            <Paper className={this.props.classes.paper}>
              {this.renderPoster(itemDetail)}
            </Paper>
            <span style={{ color: 'white' }}>
              <Typography color="inherit">
                Rent:
                {availabilities.rent ? (
                  <div>{this.renderOfferDetails(availabilities.rent)}</div>
                ) : null}
              </Typography>
              <Typography color="inherit">
                Buy:
                {availabilities.buy ? (
                  <div>{this.renderOfferDetails(availabilities.buy)}</div>
                ) : null}
              </Typography>
            </span>
          </div>
        </div>
      </React.Fragment>
    );
  };

  render() {
    return this.props.isAuthed ? (
      this.renderItemDetails()
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps: (appState: AppState) => OwnProps = appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isFetching: appState.itemDetail.fetching,
    itemDetail: R.path<Thing>(['itemDetail', 'itemDetail', 'data'], appState),
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      fetchItemDetails,
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
