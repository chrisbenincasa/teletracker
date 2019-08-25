import {
  createStyles,
  LinearProgress,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  Grid,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from '../../types';
import { retrievePopular } from '../../actions/popular';
import _ from 'lodash';
import ItemCard from '../../components/ItemCard';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
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

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
  popular?: any;
}

interface DispatchProps {
  retrievePopular: () => any;
}

type Props = OwnProps & InjectedProps & DispatchProps & WithUserProps;

class Popular extends Component<Props> {
  componentDidMount() {
    this.props.retrievePopular();
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPopular = () => {
    let { classes, popular, userSelf } = this.props;
    // console.log(this.props.popular);
    return popular && popular.payload && popular.payload.length ? (
      <div className={classes.layout}>
        <Typography color="inherit" variant="h5">
          Popular
        </Typography>
        <Grid container spacing={2}>
          {popular.payload.map(result => {
            return (
              <ItemCard
                key={result.id}
                userSelf={userSelf}
                item={result}
                itemCardVisible={false}
                // addButton
              />
            );
          })}
        </Grid>
      </div>
    ) : null;
  };

  render() {
    return this.props.isAuthed && this.props.popular ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPopular()}</div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    searchResults: R.path<Thing[]>(['search', 'results', 'data'], appState),
    popular: appState.popular,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Popular),
  ),
);
