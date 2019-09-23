import {
  Button,
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
import DeleteIcon from '@material-ui/icons/Delete';
import EditIcon from '@material-ui/icons/Edit';
import SettingsIcon from '@material-ui/icons/Settings';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
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
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
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
}

type NotOwnProps = RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

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
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let listId = Number(this.props.match.params.id);
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
    this.props.retrieveList({
      listId: this.listId,
      force: true,
    });
  }

  componentDidUpdate(oldProps: Props, prevState: State) {
    if (
      this.listId !== Number(oldProps.match.params.id) ||
      (!prevState.loadingList && this.state.loadingList)
    ) {
      this.setState({ loadingList: true });

      this.props.retrieveList({
        listId: this.listId,
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

  setWatchedSetting = event => {
    let { updateList } = this.props;
    let { list } = this.state;

    if (list) {
      let listOptions = getOrInitListOptions(list);
      let newListOptions = {
        ...listOptions,
        removeWatchedItems: !this.state.deleteOnWatch,
      };

      updateList({
        listId: this.listId,
        options: newListOptions,
      });

      this.setState({ deleteOnWatch: !this.state.deleteOnWatch });
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
          <SettingsIcon />
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
              <EditIcon />
            </ListItemIcon>
            Rename List
          </MenuItem>
          <MenuItem onClick={this.handleDeleteModalOpen}>
            <ListItemIcon>
              <DeleteIcon />
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
    let { classes, userSelf, match } = this.props;
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

  renderListDetail(list: List) {
    let { classes, listLoading, thingsById, userSelf } = this.props;
    let { deleted } = this.state;

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

              {this.renderProfileMenu()}
            </div>
            <Grid container spacing={2}>
              {list.things.map(item =>
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
