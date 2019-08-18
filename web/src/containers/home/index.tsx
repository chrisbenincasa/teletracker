import {
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import classNames from 'classnames';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import ItemCard from '../../components/ItemCard';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from '../../types';

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

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
}

class Home extends Component<Props & WithUserProps> {
  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderSearchResults = () => {
    let { classes, searchResults, userSelf } = this.props;
    searchResults = searchResults || [];

    return this.props.isSearching ? (
      this.renderLoading()
    ) : (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
        }}
      >
        {searchResults.length ? (
          <div className={classNames(classes.layout)}>
            <Grid container spacing={2}>
              {searchResults.map(result => {
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
        ) : null}
      </div>
    );
  };

  render() {
    return this.props.isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>
        {this.renderSearchResults()}
      </div>
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
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Home),
  ),
);
