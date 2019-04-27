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
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from '../../types/external/themoviedb/Movie';
import { getPosterPath } from '../../utils/metadata-access';
import { TeletrackerApi } from '../../utils/api-client';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardGrid: {
      padding: `${theme.spacing.unit * 8}px 0`,
    },
    title: {
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
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
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  isFetching: boolean;
  itemDetail?: Thing;
}

class ItemDetail extends Component<Props & WithUserProps> {
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
    let { itemDetail } = this.props;
    // itemDetail = itemDetail || {};

    console.log(this.props);

    return this.props.isFetching ? (
      this.renderLoading()
    ) : (
      <div>
        test
        {/* {this.props.match.params.type}
        {this.props.match.params.id} */}
      </div>
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

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isFetching: appState.itemDetail.fetching,
    itemDetail: R.path<Thing>(['itemDetail', 'data'], appState),
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(ItemDetail),
  ),
);
