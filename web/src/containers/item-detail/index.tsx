import {
  CardMedia,
  createStyles,
  CssBaseline,
  Grid,
  LinearProgress,
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
import { getPosterPath } from '../../utils/metadata-access';
import { TeletrackerApi } from '../../utils/api-client';
import { fetchItemDetails } from '../../actions/item-detail';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
  });

interface OwnProps {
  isAuthed: boolean;
  isFetching: boolean;
  itemDetail?: Thing;
  match: any; //?
}

interface DispatchProps {
  fetchItemDetails: (id: number) => void;
}

interface RouteParams {
  id: string;
}

type Props = DispatchProps &
OwnProps &
RouteComponentProps<RouteParams> &
WithStyles<typeof styles> &
WithUserProps;

interface State {
  currentId: number;
}
class ItemDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      currentId: 70,
    };
  }

  componentDidMount() {
    // this.props.renderItemDetails(this.props.match.params.id);
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
    let itemId = Number(this.props.match.params.id);
    let itemType = String(this.props.match.params.type);


    console.log(this.props);

    console.log(itemId);
    console.log(itemType);

    this.setState({currentId: match.params.id});

    return this.props.isFetching ? (
      this.renderLoading()
    ) : (
      this.props.fetchItemDetails(match.params.id)
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
    itemDetail: R.path<Thing>(['itemDetail', 'data'], appState),
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
      )(ItemDetail),
    ),
  ),
);
