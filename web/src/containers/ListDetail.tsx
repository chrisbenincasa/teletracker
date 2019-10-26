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
import { Delete, Edit, Settings, Tune } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import ReactGA from 'react-ga';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  deleteList,
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
  LIST_RETRIEVE_INITIATED,
  updateList,
  UserDeleteListPayload,
  UserUpdateListPayload,
} from '../actions/lists';
import ItemCard from '../components/ItemCard';
import { getTypeFromUrlParam } from '../components/Filters/TypeToggle';
import { getNetworkTypeFromUrlParam } from '../components/Filters/NetworkSelect';
import { getSortFromUrlParam } from '../components/Filters/SortDropdown';
import { getGenreFromUrlParam } from '../components/Filters/GenreSelect';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import { StdRouterLink } from '../components/RouterLink';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { ThingMap } from '../reducers/item-detail';
import { ListsByIdMap } from '../reducers/lists';
import { Genre, ItemType, List, ListSortOptions, NetworkType } from '../types';
import { getOrInitListOptions } from '../utils/list-utils';
import { Item } from '../types/v2/Item';
import { FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    listHeader: {
      margin: `${theme.spacing(2)}px 0`,
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
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: `0 ${theme.spacing(2)}px`,
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
      marginLeft: theme.spacing(1),
      marginRight: theme.spacing(1),
      width: 200,
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  listBookmark?: string;
  listsById: ListsByIdMap;
  listLoading: boolean;
  thingsById: ThingMap;
}

interface DispatchProps {
  deleteList: (payload: UserDeleteListPayload) => void;
  retrieveList: (payload: ListRetrieveInitiatedPayload) => void;
  updateList: (payload: UserUpdateListPayload) => void;
}

interface RouteParams {
  genre?: any;
  id: string;
  sort?: ListSortOptions;
  type?: 'movie' | 'show';
}

interface StateProps {
  genres?: Genre[];
}

interface WidthProps {
  width: string;
}

type NotOwnProps = RouteComponentProps<RouteParams> &
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
  prevListId: number;
  renameDialogOpen: boolean;
  showFilter: boolean;
  filters: FilterParams;
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let listId = Number(this.props.match.params.id);
    let params = new URLSearchParams(location.search);

    this.state = {
      anchorEl: null,
      deleted: false,
      deleteConfirmationOpen: false,
      deleteOnWatch: true,
      list: props.listsById[listId],
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
      filters: parseFilterParamsFromQs(this.props.location.search),
    };
  }

  static getDerivedStateFromProps(props: Props, state: State) {
    let newId = Number(props.match.params.id);

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

  get listId() {
    return Number(this.props.match.params.id);
  }

  retrieveList() {
    const {
      filters: { itemTypes, sortOrder, genresFilter },
    } = this.state;

    this.props.retrieveList({
      listId: this.listId,
      sort: sortOrder === 'default' ? undefined : sortOrder,
      itemTypes,
      genres: genresFilter ? genresFilter : undefined,
      force: true,
    });
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;
    const { match } = this.props;

    if (match && match.params && match.params.sort) {
      this.setState({
        filters: {
          ...this.state.filters,
          sortOrder: match.params.sort,
        },
      });
    }

    this.setState({ loadingList: true });
    this.retrieveList();

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }
  }

  componentDidUpdate(oldProps: Props, prevState: State) {
    const {
      filters: { itemTypes, sortOrder },
    } = this.state;

    if (
      this.listId !== Number(oldProps.match.params.id) ||
      (!prevState.loadingList && this.state.loadingList)
    ) {
      this.setState({ loadingList: true });

      this.props.retrieveList({
        listId: this.listId,
        sort: sortOrder === 'default' ? undefined : sortOrder,
        itemTypes,
        force: true,
      });
    } else if (
      !this.props.listLoading &&
      (oldProps.listLoading || this.state.loadingList)
    ) {
      this.setState({
        loadingList: false,
        list: this.props.listsById[this.listId],
        deleteOnWatch: this.props.listsById[this.listId]
          ? R.path(
              ['list', 'configuration', 'options', 'removeWatchedItems'],
              this.props,
            ) || false
          : false,
      });
    }

    if (this.state.filters.genresFilter !== prevState.filters.genresFilter) {
      this.retrieveList();
    }
  }

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
    let { deleteList, userSelf, match } = this.props;

    if (userSelf) {
      deleteList({
        listId: Number(match.params.id),
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
    let { updateList, userSelf, match } = this.props;
    let { newListName } = this.state;

    if (userSelf) {
      updateList({
        listId: Number(match.params.id),
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
              label="Delete after watched"
            />
          </MenuItem>
        </Menu>
      </div>
    );
  }

  renderDialog() {
    let { classes, userSelf, match } = this.props;
    let { deleteConfirmationOpen, migrateListId } = this.state;

    return (
      <div>
        <Dialog
          open={deleteConfirmationOpen}
          onClose={this.handleDeleteModalClose}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title">{'Delete List?'}</DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Are you sure you want to delete this list? There is no way to undo
              this. All of the content from your list can be deleted or migrated
              to another list.
            </DialogContentText>
            {/* TODO: Only show this if there is content in the list */}
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
                      item.id !== Number(match.params.id) && (
                        <MenuItem key={item.id} value={item.id}>
                          {item.name}
                        </MenuItem>
                      ),
                  )}
              </Select>
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleDeleteModalClose} color="primary">
              Cancel
            </Button>
            <Button onClick={this.handleDeleteList} color="primary" autoFocus>
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
          <DialogTitle id="alert-dialog-title">
            {'Update List Name'}
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
            <Button onClick={this.handleRenameModalClose} color="primary">
              Cancel
            </Button>
            <Button onClick={this.handleRenameList} color="primary" autoFocus>
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

  renderLoadingCircle() {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: 200,
          height: '100%',
        }}
      >
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  }

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  setFilters = (filters: FilterParams) => {
    this.setState(
      {
        filters,
      },
      () => {
        this.retrieveList();
      },
    );
  };

  setSortOrder = (sortOrder: ListSortOptions) => {
    if (this.state.filters.sortOrder !== sortOrder) {
      this.setState(
        {
          filters: {
            ...this.state.filters,
            sortOrder,
          },
          loadingList: true,
        },
        () => {
          this.retrieveList();
        },
      );
    }
  };

  setType = (type?: ItemType[]) => {
    this.setState(
      {
        filters: {
          ...this.state.filters,
          itemTypes: type,
        },
      },
      () => {
        this.retrieveList();
      },
    );
  };

  setGenre = (genres?: number[]) => {
    this.setState(
      {
        filters: {
          ...this.state.filters,
          genresFilter: genres,
        },
      },
      () => {
        this.retrieveList();
      },
    );
  };

  setNetworks = (networks?: NetworkType[]) => {
    // Only update and hit endpoint if there is a state change
    if (this.state.filters.networks !== networks) {
      this.setState(
        {
          filters: {
            ...this.state.filters,
            networks,
          },
        },
        () => {
          this.retrieveList();
        },
      );
    }
  };

  filteredFilmography(list: List) {
    let filmography = list!.items || [];

    return filmography.filter(
      (item: Item) =>
        !this.state.filters.genresFilter ||
        (item &&
          item.genres &&
          item.genres
            .map(g => g.id)
            .includes(this.state.filters.genresFilter[0])),
    );
  }

  loadMoreDebounced = _.debounce(() => {
    let {
      filters: { sortOrder, itemTypes, genresFilter },
    } = this.state;
    let { listBookmark } = this.props;

    this.props.retrieveList({
      listId: this.listId,
      bookmark: listBookmark,
      sort: sortOrder === 'default' ? undefined : sortOrder,
      itemTypes,
      genres: genresFilter ? genresFilter : undefined,
    });
  }, 200);

  loadMoreList() {
    if (this.props.listBookmark && !this.props.listLoading) {
      this.loadMoreDebounced();
    }
  }

  renderListDetail(list: List) {
    const { classes, genres, listLoading, thingsById, userSelf } = this.props;
    const {
      deleted,
      loadingList,
      showFilter,
      filters: { itemTypes, genresFilter, networks, sortOrder },
    } = this.state;

    if ((!listLoading && !list) || deleted) {
      return <Redirect to="/" />;
    } else {
      return (
        <div className={classes.root}>
          <div className={classes.listContainer}>
            <div className={classes.listHeader}>
              <div className={classes.listNameContainer}>
                <Typography
                  component={props => StdRouterLink('/lists/' + list.id, props)}
                  variant="h4"
                  align="left"
                  className={classes.listName}
                >
                  {list.name}
                </Typography>
              </div>
              <ActiveFilters
                genres={genres}
                updateFilters={this.setFilters}
                isListDynamic={list.isDynamic}
                filters={this.state.filters}
              />
              <IconButton
                onClick={this.toggleFilters}
                className={classes.settings}
                color={showFilter ? 'secondary' : 'inherit'}
              >
                <Tune />
                <Typography variant="srOnly">Tune</Typography>
              </IconButton>
              {this.renderProfileMenu()}
            </div>
            <AllFilters
              genres={genres}
              open={showFilter}
              handleTypeChange={this.setType}
              handleGenreChange={this.setGenre}
              handleNetworkChange={this.setNetworks}
              handleSortChange={this.setSortOrder}
              isListDynamic={list.isDynamic}
            />
            {!loadingList ? (
              <InfiniteScroll
                pageStart={0}
                loadMore={() => this.loadMoreList()}
                hasMore={Boolean(this.props.listBookmark)}
                useWindow
              >
                <Grid container spacing={2}>
                  {(list!.items || []).map(item =>
                    thingsById[item.id] ? (
                      <ItemCard
                        key={item.id}
                        userSelf={userSelf}
                        item={thingsById[item.id]}
                        listContext={list}
                        withActionButton
                        hoverDelete
                      />
                    ) : null,
                  )}
                </Grid>
              </InfiniteScroll>
            ) : (
              this.renderLoadingCircle()
            )}
          </div>
          {this.renderDialog()}
          {this.renderRenameDialog(list)}
        </div>
      );
    }
  }

  render() {
    let { userSelf } = this.props;
    let { list } = this.state;

    return !list || !userSelf
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
    listBookmark: appState.lists.currentBookmark,
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
      withRouter(
        connect(
          mapStateToProps,
          mapDispatchToProps,
        )(ListDetail),
      ),
    ),
  ),
);
