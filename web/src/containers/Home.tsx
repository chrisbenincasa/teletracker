import {
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Error as ErrorIcon } from '@material-ui/icons';
import Thing from '../types/Thing';

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
  error: boolean;
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
    let { searchResults, userSelf } = this.props;
    let firstLoad = !searchResults;
    searchResults = searchResults || [];

    return this.props.isSearching ? (
      this.renderLoading()
    ) : !this.props.error ? (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
        }}
      >
        {searchResults.length ? (
          <div style={{ margin: 24, padding: 8 }}>
            <Grid container spacing={2}>
              {searchResults.map(result => {
                return (
                  <ItemCard key={result.id} userSelf={userSelf} item={result} />
                );
              })}
            </Grid>
          </div>
        ) : firstLoad ? null : (
          <Typography variant="h5" gutterBottom align="center">
            No results :(
          </Typography>
        )}
      </div>
    ) : (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          flexGrow: 1,
          alignItems: 'center',
          marginTop: 25,
        }}
      >
        <ErrorIcon color="inherit" fontSize="large" />
        <Typography variant="h5" gutterBottom align="center">
          Something went wrong :(
        </Typography>
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
    // TODO: Pass SearchResult object that either contains error or a response
    error: appState.search.error,
    searchResults: appState.search.results,
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
