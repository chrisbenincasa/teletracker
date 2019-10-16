import {
  Button,
  ButtonGroup,
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { retrievePopular } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import TypeToggle, {
  getTypeFromUrlParam,
} from '../components/Filters/TypeToggle';
import withUser, { WithUserProps } from '../components/withUser';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import Featured from '../components/Featured';
import Thing from '../types/Thing';
import { ItemTypes } from '../types';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';

const limit = 20;

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  popular?: string[];
  thingsBySlug: { [key: string]: Thing };
  loading: boolean;
  bookmark?: string;
}

interface RouteParams {
  id: string;
}

interface WidthProps {
  width: string;
}

interface DispatchProps {
  retrievePopular: (payload: PopularInitiatedActionPayload) => void;
}

type Props = OwnProps &
  InjectedProps &
  DispatchProps &
  WithUserProps &
  WidthProps &
  RouteComponentProps<RouteParams>;

interface State {
  mainItemIndex: number;
  type?: ItemTypes;
}

class Popular extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      type: getTypeFromUrlParam(),
      mainItemIndex: -1,
    };
  }

  loadPopular(passBookmark: boolean) {
    this.props.retrievePopular({
      itemTypes: this.state.type,
      limit,
      bookmark: passBookmark ? this.props.bookmark : undefined,
    });
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.loadPopular(false);

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }
  }

  componentDidUpdate(prevProps, prevState) {
    const { popular, thingsBySlug } = this.props;
    const { mainItemIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
      const highestRated = popular.filter(item => {
        const thing = thingsBySlug[item];
        const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
        const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
        return voteAverage > 7 && voteCount > 1000;
      });

      const randomItem = Math.floor(Math.random() * highestRated.length);
      const popularItem = popular.findIndex(
        name => name === highestRated[randomItem],
      );

      this.setState({
        mainItemIndex: popularItem,
      });
    }
  }

  setType = (type: ItemTypes) => {
    this.setState(
      {
        type,
      },
      () => {
        this.loadPopular(false);
      },
    );
  };

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  debounceLoadMore = _.debounce(() => {
    this.loadPopular(true);
  }, 250);

  loadMoreResults = () => {
    if (!this.props.loading) {
      this.debounceLoadMore();
    }
  };

  renderPopular = () => {
    const { popular, userSelf, thingsBySlug } = this.props;
    const { type } = this.state;

    return popular && popular && popular.length ? (
      <div
        style={{
          padding: 8,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'row' }}>
          <Typography
            color="inherit"
            variant={
              ['xs', 'sm', 'md'].includes(this.props.width) ? 'h6' : 'h4'
            }
            style={{ flexGrow: 1 }}
          >
            {`Popular ${
              type
                ? type.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            marginBottom: 8,
            justifyContent: 'flex-end',
          }}
        >
          <TypeToggle handleChange={this.setType} />
        </div>
        <InfiniteScroll
          pageStart={0}
          loadMore={() => this.loadMoreResults()}
          hasMore={Boolean(this.props.bookmark)}
          useWindow
          threshold={400}
        >
          <Grid container spacing={2}>
            {popular.map((result, index) => {
              let thing = thingsBySlug[result];
              if (thing && index !== this.state.mainItemIndex) {
                return (
                  <ItemCard key={result} userSelf={userSelf} item={thing} />
                );
              } else {
                return null;
              }
            })}
          </Grid>
        </InfiniteScroll>
      </div>
    ) : null;
  };

  render() {
    const { mainItemIndex } = this.state;
    const { popular, thingsBySlug } = this.props;

    return popular ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        <Featured featuredItem={thingsBySlug[popular[mainItemIndex]]} />
        {this.renderPopular()}
      </div>
    ) : (
      this.renderLoading()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    popular: appState.popular.popular,
    thingsBySlug: appState.itemDetail.thingsBySlug,
    loading: appState.popular.loadingPopular,
    bookmark: appState.popular.popularBookmark,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(
        connect(
          mapStateToProps,
          mapDispatchToProps,
        )(Popular),
      ),
    ),
  ),
);
