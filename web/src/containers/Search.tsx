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
import { search } from '../actions/search';
import { Redirect } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import Thing from '../types/Thing';
import { Error as ErrorIcon } from '@material-ui/icons';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';

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

interface OwnProps extends WithStyles<typeof styles> {
  error: boolean;
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
  currentSearchText?: string;
}

interface DispatchProps {
  search: (text: string) => void;
}

type Props = OwnProps & WithUserProps & DispatchProps;

class Search extends Component<Props> {
  constructor(props: Props) {
    super(props);

    let params = new URLSearchParams(location.search);
    let query;
    let param = params.get('q');

    if (param && param.length > 0) {
      query = decodeURIComponent(param);

      this.state = {
        ...this.state,
        searchText: query,
      };

      if (this.props.currentSearchText !== query) {
        this.props.search(query);
      }
    }
  }

  componentDidMount() {
    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
  }

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
            <Typography>
              {`Movies & TV Shows that match "${this.props.currentSearchText}"`}
            </Typography>
            <Grid container spacing={2}>
              {searchResults.map(result => {
                return (
                  <ItemCard key={result.id} userSelf={userSelf} item={result} />
                );
              })}
            </Grid>
          </div>
        ) : firstLoad ? null : (
          <div style={{ margin: 24, padding: 8 }}>
            <Typography variant="h5" gutterBottom align="center">
              No results :(
            </Typography>
          </div>
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
    currentSearchText: R.path<string>(
      ['search', 'currentSearchText'],
      appState,
    ),
    isSearching: appState.search.searching,
    // TODO: Pass SearchResult object that either contains error or a response
    error: appState.search.error,
    searchResults: appState.search.results,
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      search,
    },
    dispatch,
  );
};

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Search),
  ),
);
