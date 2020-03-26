import {
  CircularProgress,
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
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  ExploreInitiatedActionPayload,
  retrieveExplore,
} from '../actions/explore';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Genre, ItemType, Network } from '../types';
import { Item } from '../types/v2/Item';
import { filterParamsEqual } from '../utils/changeDetection';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilter,
  updateUrlParamsForFilterRouter,
} from '../utils/urlHelper';
import { calculateLimit, getNumColumns } from '../utils/list-utils';
import CreateDynamicListDialog from '../components/Dialogs/CreateDynamicListDialog';
import {
  peopleFetchInitiated,
  PeopleFetchInitiatedPayload,
} from '../actions/people/get_people';
import withRouter, { WithRouterProps } from 'next/dist/client/with-router';
import qs from 'querystring';

const styles = (theme: Theme) =>
  createStyles({
    layout: {
      display: 'flex',
      flexGrow: 1,
      flexDirection: 'column',
    },
    listTitle: {
      display: 'flex',
      flexDirection: 'row',
      alignItems: 'center',
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    exploreContainer: {
      padding: theme.spacing(3),
      display: 'flex',
      flexDirection: 'column',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
  });

interface OwnProps {
  initialType?: ItemType;
}

interface InjectedProps {
  bookmark?: string;
  isAuthed: boolean;
  loading: boolean;
  items?: string[];
  itemsById: { [key: string]: Item };
  personNameByCanonicalId: { [key: string]: string };
  genres?: Genre[];
  networks?: Network[];
}

interface RouteParams {
  id: string;
}

interface WidthProps {
  width: string;
}

interface DispatchProps {
  retrieveItems: (payload: ExploreInitiatedActionPayload) => void;
  retrievePeople: (payload: PeopleFetchInitiatedPayload) => void;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  InjectedProps &
  DispatchProps &
  WithUserProps &
  WidthProps &
  WithRouterProps;

interface State {
  showFilter: boolean;
  filters: FilterParams;
  defaultFilterState: FilterParams;
  totalLoadedImages: number;
  createDynamicListDialogOpen: boolean;
}

class Explore extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    let defaultFilterParams: FilterParams = {
      sortOrder: 'recent',
    };

    if (props.initialType) {
      defaultFilterParams.itemTypes = [props.initialType];
    }

    let filterParams = R.mergeDeepRight(
      defaultFilterParams,
      R.filter(R.compose(R.not, R.isNil))(
        parseFilterParamsFromQs(qs.stringify(props.router.query)),
      ),
    ) as FilterParams;

    this.state = {
      ...this.state,
      showFilter: false,
      defaultFilterState: defaultFilterParams,
      filters: filterParams,
      totalLoadedImages: 0,
      createDynamicListDialogOpen: false,
    };
  }

  loadItems(passBookmark: boolean, firstRun?: boolean) {
    const {
      filters: {
        itemTypes,
        sortOrder,
        genresFilter,
        networks,
        sliders,
        people,
      },
    } = this.state;
    const { bookmark, retrieveItems, width } = this.props;

    // To do: add support for sorting
    if (!this.props.loading) {
      retrieveItems({
        bookmark: passBookmark ? bookmark : undefined,
        itemTypes,
        limit: calculateLimit(width, 3, firstRun ? 1 : 0),
        networks,
        genres: genresFilter,
        sort: _.isUndefined(sortOrder) ? 'recent' : sortOrder,
        releaseYearRange:
          sliders && sliders.releaseYear
            ? {
                min: sliders.releaseYear.min,
                max: sliders.releaseYear.max,
              }
            : undefined,
        cast: people,
      });
    }
  }

  componentDidMount() {
    const { isLoggedIn, userSelf, personNameByCanonicalId } = this.props;
    const { filters } = this.state;

    if (filters.people) {
      let missingPeople = _.filter(filters.people, person =>
        _.isUndefined(personNameByCanonicalId[person]),
      );

      if (missingPeople.length > 0) {
        this.props.retrievePeople({ ids: missingPeople });
      }
    }

    if (!this.props.items) {
      this.loadItems(false, true);
    }

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

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      if (
        _.xor(this.state.filters.itemTypes || [], filterParams.itemTypes || [])
          .length !== 0
      ) {
        let newPath;
        if (!filterParams.itemTypes || filterParams.itemTypes.length > 1) {
          newPath = 'all';
        } else if (filterParams.itemTypes[0] === 'movie') {
          newPath = 'movies';
        } else {
          newPath = 'shows';
        }

        let queryString = qs.stringify(this.props.router.query);
        this.props.router.replace(`/${newPath}?${queryString}`);
      }

      this.setState(
        {
          filters: filterParams,
        },
        () => {
          updateUrlParamsForFilterRouter(this.props, filterParams, ['type']);
          this.loadItems(false);
        },
      );
    }
  };

  createListFromFilters = () => {
    this.setState({
      createDynamicListDialogOpen: true,
    });
  };

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  mapGenre = (genre: number) => {
    const { genres } = this.props;
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  renderLoadingCircle() {
    const { classes } = this.props;
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  }

  debounceLoadMore = _.debounce(() => {
    this.loadItems(true);
  }, 250);

  loadMoreResults = () => {
    const { totalLoadedImages } = this.state;
    const { loading, items, width } = this.props;
    const numColumns = getNumColumns(width);

    // If an item is featured, update total items accordingly
    const totalFetchedItems = (items && items.length - 1) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const loadMore = totalNonLoadedImages <= numColumns;

    if (!loading && loadMore) {
      this.debounceLoadMore();
    }
  };

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  handleCreateDynamicModalClose = () => {
    this.setState({ createDynamicListDialogOpen: false });
  };

  renderPopular = () => {
    const { classes, genres, items, userSelf, itemsById } = this.props;
    const {
      filters: { genresFilter, itemTypes },
      createDynamicListDialogOpen,
    } = this.state;
    const filtersCTA = this.state.showFilter ? 'Hide Filters' : 'Filters';

    return items ? (
      <div className={classes.exploreContainer}>
        <div className={classes.listTitle}>
          <Typography
            color="inherit"
            variant={['xs', 'sm'].includes(this.props.width) ? 'h6' : 'h4'}
            style={{ flexGrow: 1 }}
          >
            {`Explore ${
              genresFilter && genresFilter.length === 1
                ? this.mapGenre(genresFilter[0])
                : ''
            } ${
              itemTypes && itemTypes.length === 1
                ? itemTypes.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
          {/* TODO: put some copy here explaining what the Explore page is */}
          <ShowFiltersButton onClick={this.toggleFilters} />
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={this.handleFilterParamsChange}
            isListDynamic={false}
            filters={this.state.filters}
            variant="default"
            defaultSort="recent"
          />
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          filters={this.state.filters}
          updateFilters={this.handleFilterParamsChange}
        />
        <InfiniteScroll
          pageStart={0}
          loadMore={this.loadMoreResults}
          hasMore={Boolean(this.props.bookmark)}
          useWindow
          threshold={300}
        >
          <Grid container spacing={2}>
            {items.map(result => {
              let thing = itemsById[result];
              if (thing) {
                return (
                  <ItemCard
                    key={result}
                    userSelf={userSelf}
                    item={thing}
                    hasLoaded={this.setVisibleItems}
                  />
                );
              } else {
                return null;
              }
            })}
          </Grid>
          {this.props.loading && this.renderLoadingCircle()}
        </InfiniteScroll>

        <CreateDynamicListDialog
          filters={this.state.filters}
          open={createDynamicListDialogOpen}
          onClose={this.handleCreateDynamicModalClose}
          networks={this.props.networks || []}
          genres={this.props.genres || []}
        />
      </div>
    ) : null;
  };

  render() {
    const { classes, items, loading } = this.props;

    return (
      <div className={classes.layout}>
        <LinearProgress
          style={{ visibility: loading || !items ? 'visible' : 'hidden' }}
        />
        {this.renderPopular()}
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    items: appState.explore.items,
    itemsById: appState.itemDetail.thingsById,
    personNameByCanonicalId: appState.people.nameByIdOrSlug,
    loading:
      appState.explore.loadingExplore ||
      appState.metadata.metadataLoading ||
      appState.people.loadingPeople,
    genres: appState.metadata.genres,
    networks: appState.metadata.networks,
    bookmark: appState.explore.exploreBookmark,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrieveItems: retrieveExplore,
      retrievePeople: peopleFetchInitiated,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(connect(mapStateToProps, mapDispatchToProps)(Explore)),
    ),
  ),
);
