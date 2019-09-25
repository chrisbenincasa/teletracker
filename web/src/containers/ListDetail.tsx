import {
  Button,
  ButtonGroup,
  Chip,
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
} from '@material-ui/core';
import { Delete, Edit, Settings, Tune } from '@material-ui/icons';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  Redirect,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
} from '../actions/lists';
import {
  deleteList,
  updateList,
  UserDeleteListPayload,
  UserUpdateListPayload,
} from '../actions/lists';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { layoutStyles } from '../styles';
import { List } from '../types';
import _ from 'lodash';
import { StdRouterLink } from '../components/RouterLink';
import { ThingMap } from '../reducers/item-detail';
import { getOrInitListOptions } from '../utils/list-utils';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';
import { Genre } from '../types';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    listHeader: {
      margin: `${theme.spacing(2)}px 0`,
      display: 'flex',
      flex: '1 0 auto',
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    listName: {
      textDecoration: 'none',
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: theme.palette.text.primary,
      },
    },
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
      whiteSpace: 'nowrap',
    },
    filterSortContainer: {
      [theme.breakpoints.up('sm')]: {
        display: 'flex',
      },
      marginBottom: 8,
      flexGrow: 1,
    },
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    genre: { margin: 5 },
    genreContainer: { display: 'flex', flexWrap: 'wrap' },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: `0 ${theme.spacing(2)}px`,
    },
    textField: {
      marginLeft: theme.spacing(1),
      marginRight: theme.spacing(1),
      width: 200,
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  listLoading: boolean;
  listsById: ListsByIdMap;
  thingsById: ThingMap;
}

interface DispatchProps {
  retrieveList: (payload: ListRetrieveInitiatedPayload) => void;
  deleteList: (payload: UserDeleteListPayload) => void;
  updateList: (payload: UserUpdateListPayload) => void;
}

interface RouteParams {
  id: string;
  sort?: 'popularity' | 'recent' | 'added_time' | 'default';
}

interface StateProps {
  genres?: Genre[];
}

type NotOwnProps = RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps &
  StateProps;

type Props = OwnProps & NotOwnProps;

interface State {
  loadingList: boolean;
  deleteConfirmationOpen: boolean;
  deleted: boolean;
  anchorEl: HTMLElement | null;
  migrateListId: number;
  renameDialogOpen: boolean;
  newListName: string;
  prevListId: number;
  deleteOnWatch: boolean;
  list?: List;
  showFilter: boolean;
  sortOrder: string;
  filter: number;
  genre?: string;
  type?: 'movie' | 'show';
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let listId = Number(this.props.match.params.id);
    let params = new URLSearchParams(location.search);
    let sort;
    let param = params.get('sort');
    if (param === 'recent' || param === 'added_time') {
      sort = param;
    } else {
      sort = 'popularity';
    }

    this.state = {
      loadingList: true,
      deleteConfirmationOpen: false,
      deleted: false,
      anchorEl: null,
      migrateListId: 0,
      renameDialogOpen: false,
      newListName: '',
      prevListId: listId,
      list: props.listsById[listId],
      deleteOnWatch: true,
      showFilter: params.has('sort') || params.has('genre'),
      sortOrder: sort,
      filter: -1,
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

  componentDidMount() {
<<<<<<< HEAD
    const { isLoggedIn, userSelf } = this.props;
=======
    const { match } = this.props;
    let sortBy;

    if (match && match.params && match.params.sort) {
      sortBy = match.params.sort;
      this.setState({ sortOrder: sortBy });
    } else {
      sortBy = 'default';
    }
>>>>>>> 43cf990... url param work

    this.props.retrieveList({
      listId: this.listId,
      sort: sortBy,
      force: true,
    });

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }
  }

  componentDidUpdate(oldProps: Props, prevState: State) {
    const { match } = this.props;

    if (
      this.listId !== Number(oldProps.match.params.id) ||
      (!prevState.loadingList && this.state.loadingList)
    ) {
      this.setState({ loadingList: true });

      this.props.retrieveList({
        listId: this.listId,
        sort: (match && match.params && match.params.sort) || 'default',
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

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  setSortOrder = event => {
    const { match } = this.props;

    let params = new URLSearchParams(location.search);

    this.setState({ sortOrder: event.target.value });

    this.props.retrieveList({
      listId: this.listId,
      sort: event.target.value || 'default',
      force: true,
    });

    let paramSort = params.get('sort');
    if (paramSort) {
      params.set('sort', event.target.value);
    } else {
      params.append('sort', event.target.value);
    }
    params.sort();

    this.props.history.push(`${match.params.id}?${params}`);
  };

  setFilter = genre => {
    const { match } = this.props;
    let params = new URLSearchParams(location.search);
    let paramGenre = params.get('genre');

    if (genre === 'All' && paramGenre) {
      params.delete('genre');
      this.props.history.push(`${match.params.id}?${params}`);
      this.setState({ filter: -1 });
      return;
    }

    if (paramGenre) {
      params.set('genre', genre.slug);
    } else {
      params.append('genre', genre.slug);
    }
    params.sort();

    this.props.history.push(`${match.params.id}?${params}`);
    this.setState({ filter: genre.id });
  };

  filteredFilmography(list: List) {
    let filmography = list!.things || [];

    return filmography.filter(
      (item: Thing) =>
        (item && item.genreIds && item.genreIds.includes(this.state.filter)) ||
        this.state.filter === -1,
    );
  }

  renderFilters(list: List) {
    const { showFilter, type } = this.state;
    const { classes } = this.props;
    let filmography = list!.things || [];
    let finalGenres: Genre[] = [];
    let params = new URLSearchParams(location.search);
    console.log(list);
    if (this.props.genres) {
      finalGenres = _.chain(filmography)
        .map((f: Thing) => f.genreIds || [])
        .flatten()
        .uniq()
        .map(id => {
          let g = _.find(this.props.genres, g => g.id === id);
          if (g) {
            return [g];
          } else {
            return [];
          }
        })
        .flatten()
        .value();
    }

    // Display filtering if button was clicked or query params exist
    return showFilter ? (
      <div className={classes.genreContainer}>
        <Typography
          color="inherit"
          variant="h5"
          style={{ display: 'block', width: '100%' }}
        >
          Filter
        </Typography>
        <div className={classes.filterSortContainer}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              flexGrow: 1,
              flexWrap: 'wrap',
            }}
          >
            <Chip
              key={-1}
              label="All"
              className={classes.genre}
              onClick={() => this.setFilter('All')}
              color={this.state.filter === -1 ? 'secondary' : 'default'}
              clickable
            />
            {finalGenres.map(genre => (
              <Chip
                key={genre.id}
                label={genre.name}
                className={classes.genre}
                onClick={() => this.setFilter(genre)}
                color={this.state.filter === genre.id ? 'secondary' : 'default'}
                clickable
              />
            ))}
          </div>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by All, Movies, or just TV Shows"
            size="medium"
          >
            <Button
              color={!type ? 'secondary' : 'primary'}
              component={RouterLink}
              to={{
                pathname: location.pathname,
                search: '',
              }}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={type === 'movie' ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=movie'}
              className={classes.filterButtons}
            >
              Movies
            </Button>
            <Button
              color={type === 'show' ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=show'}
              className={classes.filterButtons}
            >
              TV
            </Button>
          </ButtonGroup>

          <div
            style={{ display: 'flex', flexDirection: 'column', marginLeft: 15 }}
          >
            <InputLabel shrink htmlFor="age-label-placeholder">
              Sort by:
            </InputLabel>
            <Select
              value={
                this.state.sortOrder
                  ? this.state.sortOrder
                  : list.isDynamic
                  ? 'popularity'
                  : 'added_time'
              }
              inputProps={{
                name: 'sortOrder',
                id: 'sort-order',
              }}
              onChange={this.setSortOrder}
            >
              <MenuItem value="added_time">Date Added</MenuItem>
              <MenuItem value="popularity">Popularity</MenuItem>
              <MenuItem value="recent">Release Date</MenuItem>
            </Select>
          </div>
        </div>
      </div>
    ) : null;
  }

  renderListDetail(list: List) {
    let { classes, listLoading, thingsById, userSelf } = this.props;
    let { deleted } = this.state;
    let params = new URLSearchParams(location.search);

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
              <IconButton
                onClick={this.toggleFilters}
                className={classes.settings}
                color={this.state.showFilter ? 'secondary' : 'inherit'}
              >
                <Tune />
                <Typography variant="srOnly">Tune</Typography>
              </IconButton>
              {this.renderProfileMenu()}
            </div>
            {this.renderFilters(list)}
            <Grid container spacing={2}>
              {this.filteredFilmography(list).map(item =>
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
          </div>
          {this.renderDialog()}
          {this.renderRenameDialog(list)}
        </div>
      );
    }
  }

  render() {
    let { userSelf } = this.props;
    let { loadingList, list } = this.state;

    return loadingList || !list || !userSelf
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

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(ListDetail),
    ),
  ),
);
