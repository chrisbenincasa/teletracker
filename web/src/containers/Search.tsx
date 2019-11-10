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
import { search, SearchInitiatedPayload } from '../actions/search';
import { Redirect } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Error as ErrorIcon } from '@material-ui/icons';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { Item } from '../types/v2/Item';

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
  searchResults?: Item[];
  currentSearchText?: string;
  searchBookmark?: string;
}

interface DispatchProps {
  search: (payload: SearchInitiatedPayload) => void;
}

type Props = OwnProps & WithUserProps & DispatchProps;

type State = {
  searchText: string;
};

class Search extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    let params = new URLSearchParams(window.location.search);
    let query;
    let param = params.get('q');

    if (param && param.length > 0) {
      query = decodeURIComponent(param);

      this.state = {
        ...this.state,
        searchText: query,
      };

      if (this.props.currentSearchText !== query) {
        this.props.search({
          query,
        });
      }
    }
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }

  debouncedSearch = _.debounce(() => {
    this.props.search({
      query: this.state.searchText,
      bookmark: this.props.searchBookmark,
    });
  }, 200);

  loadMoreResults() {
    if (!this.props.isSearching) {
      this.debouncedSearch();
    }
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

    return this.props.isSearching &&
      (!this.props.searchBookmark ||
        this.state.searchText !== this.props.currentSearchText) ? (
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
            <InfiniteScroll
              pageStart={0}
              loadMore={() => this.loadMoreResults()}
              hasMore={Boolean(this.props.searchBookmark)}
              useWindow
              threshold={300}
            >
              <Grid container spacing={2}>
                {searchResults.map(result => {
                  return (
                    <ItemCard
                      key={result.id}
                      userSelf={userSelf}
                      item={result}
                    />
                  );
                })}
              </Grid>
            </InfiniteScroll>
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
    searchBookmark: appState.search.bookmark,
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
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(Search)),
);
