import {
  CardMedia,
  createStyles,
  CssBaseline,
  Grid,
  LinearProgress,
  Paper,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { Dispatch, bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from '../../types/external/themoviedb/Movie';
import { getPosterPath, getDescription } from '../../utils/metadata-access';
import { TeletrackerApi } from '../../utils/api-client';
import { fetchItemDetails } from '../../actions/item-detail';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    paper: {
      width: '250px',
      margin: '25px',
    }
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
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />
      );
    } else {
      return null;
    }
  };

  renderItemDetails = () => {
    let { itemDetail, match } = this.props;
    let itemId = Number(match.params.id);
    let itemType = String(match.params.type);

    console.log(this.props);

    // this.setState({currentId: match.params.id});

    return this.props.isFetching || !itemDetail ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <Paper
          className={this.props.classes.paper}
        >
        {
          this.renderPoster(itemDetail)
        }{
          getDescription(itemDetail)
        }
        </Paper>
      </React.Fragment>
    )
  }

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
      fetchItemDetails
    },
    dispatch
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
