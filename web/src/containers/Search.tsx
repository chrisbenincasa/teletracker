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
import { bindActionCreators, Dispatch } from 'redux';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Error as ErrorIcon } from '@material-ui/icons';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import { Item } from '../types/v2/Item';

const styles = (theme: Theme) =>
  createStyles({
    searchError: {
      display: 'flex',
      flexDirection: 'column',
      flexGrow: 1,
      alignItems: 'center',
      marginTop: theme.spacing(3),
    },
    searchNoResults: {
      display: 'flex',
      flexGrow: 1,
      justifyContent: 'center',
      margin: theme.spacing(1),
      padding: theme.spacing(1),
    },
    searchResultsContainer: {
      margin: theme.spacing(1),
      padding: theme.spacing(1),
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

  render() {
    let {
      classes,
      currentSearchText,
      searchBookmark,
      searchResults,
      userSelf,
    } = this.props;
    let firstLoad = !searchResults;
    searchResults = searchResults || [];

    return this.props.isSearching &&
      (!searchBookmark || this.state.searchText !== currentSearchText) ? (
      this.renderLoading()
    ) : !this.props.error ? (
      <React.Fragment>
        {searchResults.length ? (
          <div className={classes.searchResultsContainer}>
            <Typography>
              {`Movies & TV Shows that match "${currentSearchText}"`}
            </Typography>
            <InfiniteScroll
              pageStart={0}
              loadMore={() => this.loadMoreResults()}
              hasMore={Boolean(searchBookmark)}
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
          <div className={classes.searchNoResults}>
            <Typography variant="h5" gutterBottom align="center">
              No results :(
            </Typography>
          </div>
        )}
      </React.Fragment>
    ) : (
      <div className={classes.searchError}>
        <ErrorIcon color="inherit" fontSize="large" />
        <Typography variant="h5" gutterBottom align="center">
          Something went wrong :(
        </Typography>
      </div>
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
