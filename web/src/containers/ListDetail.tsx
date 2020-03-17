import {
  Button,
  CircularProgress,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  LinearProgress,
  ListItemIcon,
  Menu,
  MenuItem,
  Select,
  Switch,
  TextField,
  Theme,
  Typography,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
import { Delete, Edit, Save, Settings, Tune } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import {
  deleteList,
  LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
  updateList,
  UserDeleteListPayload,
  UserUpdateListPayload,
} from '../actions/lists';
import ItemCard from '../components/ItemCard';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { ThingMap } from '../reducers/item-detail';
import { ListsByIdMap } from '../reducers/lists';
import {
  Genre,
  List,
  SortOptions,
  Network,
  ListGenreRule,
  ListNetworkRule,
  ListPersonRule,
  ListItemTypeRule,
  ListReleaseYearRule,
} from '../types';
import {
  calculateLimit,
  getNumColumns,
  getOrInitListOptions,
} from '../utils/list-utils';
import {
  FilterParams,
  isDefaultFilter,
  SlidersState,
} from '../utils/searchFilters';
import {
  parseFilterParamsFromQs,
  updateUrlParamsForFilter,
  updateUrlParamsForFilterRouter,
} from '../utils/urlHelper';
import { filterParamsEqual } from '../utils/changeDetection';
import { collect, headOption } from '../utils/collection-utils';
import { optionalSetsEqual } from '../utils/sets';
import { Person } from '../types/v2/Person';
import withRouter, { WithRouterProps } from 'next/dist/client/with-router';
import qs from 'querystring';
import Link from 'next/link';

const styles = (theme: Theme) =>
  createStyles({
    listHeader: {
      margin: theme.spacing(2, 0),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listName: {
      textDecoration: 'none',
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: theme.palette.text.primary,
      },
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: theme.spacing(0, 2),
      width: '100%',
    },
    noContent: {
      margin: theme.spacing(5),
      width: '100%',
    },
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
    textField: {
      margin: theme.spacing(0, 1),
      width: 200,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  listBookmark?: string;
  listsById: ListsByIdMap;
  listLoading: boolean;
  thingsById: ThingMap;
  personById: { [key: string]: Person };
}

interface DispatchProps {
  deleteList: (payload: UserDeleteListPayload) => void;
  retrieveList: (payload: ListRetrieveInitiatedPayload) => void;
  updateList: (payload: UserUpdateListPayload) => void;
}

interface RouteParams {
  genre?: any;
  id: string;
  sort?: SortOptions;
  type?: 'movie' | 'show';
}

interface StateProps {
  genres?: Genre[];
  networks?: Network[];
  loading: boolean;
}

interface WidthProps {
  width: string;
}

type NotOwnProps = WithRouterProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps &
  StateProps &
  WidthProps;

type Props = OwnProps & NotOwnProps;

interface State {
  anchorEl: HTMLElement | null;
  deleted: boolean;
  deleteConfirmationOpen: boolean;
  deleteOnWatch: boolean;
  genre?: string;
  list?: List;
  loadingList: boolean;
  migrateListId: number;
  newListName: string;
  prevListId: string;
  renameDialogOpen: boolean;
  showFilter: boolean;
  filters: FilterParams;
  listFilters?: FilterParams; // The default state of filters based on list rules.
  totalLoadedImages: number;
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let listId = this.props.router.query.id as string;
    let queryString = qs.stringify(props.router.query);
    let params = new URLSearchParams();

    let list = props.listsById[listId];

    let filters = parseFilterParamsFromQs(queryString);

    this.state = {
      anchorEl: null,
      deleted: false,
      deleteConfirmationOpen: false,
      deleteOnWatch: true,
      list,
      loadingList: true,
      migrateListId: 0,
      newListName: '',
      prevListId: listId,
      renameDialogOpen: false,
      // TODO: Support sliders
      showFilter:
        params.has('sort') ||
        params.has('genres') ||
        params.has('networks') ||
        params.has('types'),
      filters: filters,
      totalLoadedImages: 0,
    };
  }

  static getDerivedStateFromProps(props: Props, state: State) {
    let newId = props.router.query.id;

    if (newId !== state.prevListId) {
      return {
        ...state,
        loadingList: true,
        prevListId: newId,
      };
    } else {
      return state;
    }
  }

  get listId(): string {
    return this.props.router.query.id as string;
  }

  get currentList() {
    let list: List | undefined = this.props.listsById[this.listId];
    if (!list) {
      list = _.find(this.props.listsById, list =>
        list.aliases.includes(this.listId),
      );
    }

    return list;
  }

  makeListFilters = (
    initialLoad: boolean,
    currentFilters: FilterParams,
    initialFilters?: FilterParams,
  ): Partial<ListRetrieveInitiatedPayload> => {
    if (initialLoad) {
      return {};
    } else if (initialFilters) {
      let genres =
        currentFilters.genresFilter &&
        !optionalSetsEqual(
          currentFilters.genresFilter,
          initialFilters.genresFilter,
        )
          ? currentFilters.genresFilter || []
          : initialFilters.genresFilter;

      let networks =
        currentFilters.networks &&
        !optionalSetsEqual(currentFilters.networks, initialFilters.networks)
          ? currentFilters.networks
          : initialFilters.networks;

      let itemTypes = !optionalSetsEqual(
        currentFilters.itemTypes,
        initialFilters.itemTypes,
      )
        ? currentFilters.itemTypes
        : initialFilters.itemTypes;

      console.log(currentFilters, initialFilters);

      return {
        genres,
        networks,
        itemTypes,
      };
    } else {
      return {
        genres: currentFilters.genresFilter,
      };
    }
  };

  retrieveList(initialLoad: boolean) {
    const {
      filters: { itemTypes, sortOrder, genresFilter, networks },
    } = this.state;
    const { width } = this.props;

    this.props.retrieveList({
      listId: this.listId,
      force: true,
      limit: calculateLimit(width, 3),
      ...this.makeListFilters(
        initialLoad,
        this.state.filters,
        this.state.listFilters,
      ),
      // sort: sortOrder === 'default' ? undefined : sortOrder,
      // itemTypes,
      // genres: genresFilter ? genresFilter : undefined,
      // networks: networks ? networks : undefined,
    });
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.setState({ loadingList: true });
    this.retrieveList(true);
  }

  extractFiltersFromList = (list: List): FilterParams | undefined => {
    const { networks } = this.props;

    if (
      list.isDynamic &&
      list.configuration &&
      list.configuration.ruleConfiguration
    ) {
      let groupedRules = _.groupBy(
        list.configuration.ruleConfiguration.rules,
        'type',
      );

      let genreRules = groupedRules.UserListGenreRule
        ? groupedRules.UserListGenreRule.map(
            rule => (rule as ListGenreRule).genreId,
          )
        : undefined;

      let networkRules = groupedRules.UserListNetworkRule
        ? collect(groupedRules.UserListNetworkRule, rule => {
            let networkId = (rule as ListNetworkRule).networkId;
            let foundNetwork = _.find(networks, n => n.id === networkId);
            return foundNetwork ? foundNetwork.slug : undefined;
          })
        : undefined;

      let itemTypeRules = groupedRules.UserListItemTypeRule
        ? collect(
            groupedRules.UserListItemTypeRule,
            rule => (rule as ListItemTypeRule).itemType,
          )
        : undefined;

      let releaseYearRule = groupedRules.UserListReleaseYearRule
        ? headOption(
            collect(groupedRules.UserListReleaseYearRule, rule => {
              let typedRule = rule as ListReleaseYearRule;
              if (typedRule.minimum || typedRule.maximum) {
                return {
                  releaseYear: {
                    min: typedRule.minimum,
                    max: typedRule.maximum,
                  },
                } as SlidersState;
              }
            }),
          )
        : undefined;

      let personRules = groupedRules.UserListPersonRule
        ? collect(groupedRules.UserListPersonRule, rule => {
            let personId = (rule as ListPersonRule).personId;
            let person = this.props.personById[personId];
            return person ? person.canonical_id : undefined;
          })
        : undefined;

      return {
        genresFilter: genreRules,
        networks: networkRules,
        itemTypes: itemTypeRules,
        sliders: releaseYearRule,
        people: personRules,
        sortOrder: list.configuration.ruleConfiguration.sort
          ? list.configuration.ruleConfiguration.sort.sort
          : 'default',
      };
    }
  };

  componentDidUpdate(oldProps: Props, prevState: State) {
    const { filters, list } = this.state;

    if (
      this.listId !== oldProps.router.query.id ||
      (!prevState.loadingList && this.state.loadingList)
    ) {
      this.setState({ loadingList: true });

      this.retrieveList(true);
    } else if (
      !this.props.listLoading &&
      (oldProps.listLoading || this.state.loadingList)
    ) {
      let list = this.currentList;
      let listFilters = this.extractFiltersFromList(list!);

      this.setState({
        loadingList: false,
        list,
        deleteOnWatch: Boolean(
          list?.configuration?.options?.removeWatchedItems,
        ),
        listFilters,
      });
    } else if (!_.isEqual(list, prevState.list)) {
      // Detect deep object inequality
      this.setState({ list: list });
    }
  }

  setVisibleItems = () => {
    this.setState({
      totalLoadedImages: this.state.totalLoadedImages + 1,
    });
  };

  setWatchedSetting = () => {
    let { updateList } = this.props;
    let { deleteOnWatch, list } = this.state;

    if (list) {
      let listOptions = getOrInitListOptions(list);
      let newListOptions = {
        ...listOptions,
        removeWatchedItems: !deleteOnWatch,
      };

      updateList({
        listId: this.listId,
        options: newListOptions,
      });

      this.setState({ deleteOnWatch: !deleteOnWatch });
    }
  };

  handleMigration = event => {
    this.setState({ migrateListId: event.target.value });
  };

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ anchorEl: null });
  };

  handleDeleteList = () => {
    let { deleteList, userSelf, router } = this.props;

    if (userSelf) {
      deleteList({
        listId: this.listId,
      });

      this.setState({ deleted: true });
    }
    this.handleDeleteModalClose();
  };

  handleDeleteModalOpen = () => {
    this.handleClose();
    this.setState({ deleteConfirmationOpen: true });
  };

  handleDeleteModalClose = () => {
    this.setState({ deleteConfirmationOpen: false });
  };

  handleRenameList = () => {
    let { updateList, userSelf, router } = this.props;
    let { newListName } = this.state;

    if (userSelf) {
      updateList({
        listId: this.listId,
        name: newListName,
      });
    }
    this.handleRenameModalClose();
  };

  handleRenameModalOpen = () => {
    this.handleClose();
    this.setState({ renameDialogOpen: true });
  };

  handleRenameModalClose = () => {
    this.setState({ renameDialogOpen: false });
  };

  handleRenameChange = event => {
    this.setState({ newListName: event.target.value });
  };

  renderProfileMenu() {
    if (!this.props.isAuthed) {
      return null;
    }

    if (!this.currentList?.ownedByRequester) {
      return null;
    }

    let { anchorEl } = this.state;
    let { classes } = this.props;
    let isMenuOpen = !!anchorEl;

    return (
      <div>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          color="inherit"
          onClick={this.handleMenu}
          className={classes.settings}
        >
          <Settings />
          <Typography variant="srOnly">Settings</Typography>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={!!this.state.anchorEl}
          onClose={this.handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={this.handleRenameModalOpen}>
            <ListItemIcon>
              <Edit />
            </ListItemIcon>
            Rename List
          </MenuItem>
          <MenuItem onClick={this.handleDeleteModalOpen}>
            <ListItemIcon>
              <Delete />
            </ListItemIcon>
            Delete List
          </MenuItem>
          <MenuItem>
            <FormControlLabel
              control={
                <Switch
                  checked={this.state.deleteOnWatch}
                  onChange={this.setWatchedSetting}
                  value="checkedB"
                  color="primary"
                />
              }
              label="Automatically remove items after watching"
            />
          </MenuItem>
        </Menu>
      </div>
    );
  }

  renderDialog() {
    let { classes, userSelf, router } = this.props;
    let { deleteConfirmationOpen, migrateListId } = this.state;

    return (
      <div>
        <Dialog
          open={deleteConfirmationOpen}
          onClose={this.handleDeleteModalClose}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title" className={classes.title}>
            {`Delete "${this.state?.list?.name}" list?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              {`Are you sure you want to delete this list? There is no way to undo
              this. ${
                this.state.list && this.state.list.totalItems > 0
                  ? 'All of the content from your list can be deleted or migrated to another list.'
                  : ''
              }`}
            </DialogContentText>
            {this.state.list && this.state.list.totalItems > 0 && (
              <FormControl className={classes.formControl}>
                <InputLabel htmlFor="age-simple">
                  Migrate tracked items from this list to:
                </InputLabel>
                <Select value={migrateListId} onChange={this.handleMigration}>
                  <MenuItem value="0">
                    <em>Delete all tracked items</em>
                  </MenuItem>
                  {userSelf &&
                    _.map(
                      this.props.listsById,
                      item =>
                        item.id !== this.listId && (
                          <MenuItem key={item.id} value={item.id}>
                            {item.name}
                          </MenuItem>
                        ),
                    )}
                </Select>
              </FormControl>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleDeleteModalClose} color="primary">
              Cancel
            </Button>
            <Button
              onClick={this.handleDeleteList}
              color="primary"
              variant="contained"
              autoFocus
            >
              Delete
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  }

  renderRenameDialog(list: List) {
    let { classes } = this.props;
    let { renameDialogOpen } = this.state;

    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={renameDialogOpen}
          onClose={this.handleRenameModalClose}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title" className={classes.title}>
            {`Update "${this.state?.list?.name}" List Name?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Naming is hard, update your list name:
            </DialogContentText>
            <FormControl className={classes.formControl}>
              <TextField
                label="List Name"
                defaultValue={list.name}
                className={classes.textField}
                margin="normal"
                onChange={this.handleRenameChange}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleRenameModalClose}>Cancel</Button>
            <Button
              onClick={this.handleRenameList}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  }

  renderLoading() {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  }

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      this.setState(
        {
          filters: filterParams,
        },
        () => {
          updateUrlParamsForFilterRouter(this.props, filterParams);
          this.retrieveList(false);
        },
      );
    }
  };

  loadMoreDebounced = _.debounce(() => {
    let {
      filters: { sortOrder, itemTypes, genresFilter, networks },
    } = this.state;
    let { listBookmark, width } = this.props;

    this.props.retrieveList({
      listId: this.listId,
      bookmark: listBookmark,
      limit: calculateLimit(width, 3),
      sort: sortOrder === 'default' ? undefined : sortOrder,
      itemTypes,
      genres: genresFilter ? genresFilter : undefined,
      networks: networks ? networks : undefined,
    });
  }, 200);

  loadMoreList() {
    const { list, totalLoadedImages } = this.state;
    const { listBookmark, listLoading, width } = this.props;
    const numColumns = getNumColumns(width);
    const totalFetchedItems = (list && list.items && list.items.length) || 0;
    const totalNonLoadedImages = totalFetchedItems - totalLoadedImages;
    const loadMore = totalNonLoadedImages <= numColumns;

    if (listBookmark && !listLoading && loadMore) {
      this.loadMoreDebounced();
    }
  }

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

  renderListDetail(list: List) {
    const {
      classes,
      genres,
      listLoading,
      thingsById,
      userSelf,
      router,
    } = this.props;
    const { deleted, showFilter, filters, listFilters } = this.state;

    if ((!listLoading && !list) || deleted) {
      router.replace('/');
    }

    // if ((!listLoading && !list) || deleted) {
    //   return <Redirect to="/" />;
    // } else {
    // }
    return (
      <div className={classes.root}>
        <div className={classes.listContainer}>
          <div className={classes.listHeader}>
            <div className={classes.listNameContainer}>
              <Typography
                variant="h4"
                align="left"
                className={classes.listName}
              >
                {list.name}
              </Typography>
            </div>
            <IconButton
              onClick={this.toggleFilters}
              className={classes.settings}
              color={showFilter ? 'primary' : 'default'}
            >
              <Tune />
              <Typography variant="srOnly">Tune</Typography>
            </IconButton>
            {this.renderProfileMenu()}
            {list.isDynamic && listFilters ? (
              <IconButton
                className={classes.settings}
                color={showFilter ? 'secondary' : 'inherit'}
                disabled={
                  isDefaultFilter(filters) ||
                  filterParamsEqual(listFilters!, filters)
                }
              >
                <Save />
                <Typography variant="srOnly">Save</Typography>
              </IconButton>
            ) : null}
          </div>
          <div className={classes.filters}>
            <ActiveFilters
              genres={genres}
              updateFilters={this.handleFilterParamsChange}
              isListDynamic={list.isDynamic}
              filters={
                isDefaultFilter(filters) && listFilters ? listFilters : filters
              }
              initialState={listFilters}
              variant="default"
            />
          </div>
          <AllFilters
            genres={genres}
            filters={
              isDefaultFilter(filters) && listFilters ? listFilters : filters
            }
            updateFilters={this.handleFilterParamsChange}
            open={showFilter}
            isListDynamic={list.isDynamic}
          />
          <InfiniteScroll
            pageStart={0}
            loadMore={() => this.loadMoreList()}
            hasMore={Boolean(this.props.listBookmark)}
            useWindow
            threshold={300}
          >
            <Grid container spacing={2}>
              {list && list.items && list.items.length > 0 ? (
                list.items.map(item =>
                  thingsById[item.id] ? (
                    <ItemCard
                      key={item.id}
                      userSelf={userSelf}
                      item={thingsById[item.id]}
                      listContext={list}
                      withActionButton
                      hoverDelete={!list.isDynamic}
                      hasLoaded={this.setVisibleItems}
                    />
                  ) : null,
                )
              ) : (
                <Typography
                  align="center"
                  color="secondary"
                  display="block"
                  className={classes.noContent}
                >
                  {list.totalItems > 0 &&
                    !this.props.listLoading &&
                    'Sorry, nothing matches your current filter.  Please update and try again.'}
                  {list.totalItems === 0 &&
                    !this.props.listLoading &&
                    'Nothing has been added to this list yet.'}
                </Typography>
              )}
            </Grid>
            {this.props.listLoading && this.renderLoadingCircle()}
          </InfiniteScroll>
        </div>
        {this.renderDialog()}
        {this.renderRenameDialog(list)}
      </div>
    );
  }

  render() {
    let { loading } = this.props;
    let { list } = this.state;

    return !list || loading
      ? this.renderLoading()
      : this.renderListDetail(this.state.list!);
  }
}

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => OwnProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    listLoading: Boolean(appState.lists.loading[LIST_RETRIEVE_INITIATED]),
    listsById: appState.lists.listsById,
    thingsById: appState.itemDetail.thingsById,
    genres: appState.metadata.genres,
    networks: appState.metadata.networks,
    loading: appState.metadata.metadataLoading,
    listBookmark: appState.lists.currentBookmark,
    personById: appState.people.peopleById,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      retrieveList: ListRetrieveInitiated,
      deleteList: deleteList,
      updateList,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(connect(mapStateToProps, mapDispatchToProps)(ListDetail)),
    ),
  ),
);
