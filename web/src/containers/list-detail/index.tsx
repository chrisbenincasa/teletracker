import {
  Button,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  Grid,
  LinearProgress,
  Link,
  ListItemIcon,
  Menu,
  MenuItem,
  Select,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
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
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
} from '../../actions/lists';
import { deleteList, UserDeleteListPayload } from '../../actions/user';
import ItemCard from '../../components/ItemCard';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { ListsByIdMap } from '../../reducers/lists';
import { layoutStyles } from '../../styles';
import { List } from '../../types';
import SettingsIcon from '@material-ui/icons/Settings';
import DeleteIcon from '@material-ui/icons/Delete';
import EditIcon from '@material-ui/icons/Edit';

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
        color: '#000',
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
      flex: '1 0 auto',
      padding: `0 ${theme.spacing(2)}px`,
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  listLoading: boolean;
  listsById: ListsByIdMap;
}

interface DrawerProps {
  drawerOpen: boolean;
}

interface MenuItemProps {
  to: any;
  primary?: string;
  button?: any;
  key?: any;
  selected?: any;
  listLength?: Number;
  onClick?: any;
}

interface DispatchProps {
  retrieveList: (payload: ListRetrieveInitiatedPayload) => void;
  deleteList: (payload: UserDeleteListPayload) => void;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  loadingList: boolean;
  deleteConfirmationOpen: boolean;
  deleted: boolean;
  anchorEl: any;
  migrateListId: number;
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      loadingList: true,
      deleteConfirmationOpen: false,
      deleted: false,
      anchorEl: null,
      migrateListId: 0,
    };
  }

  componentDidMount() {
    console.log(this.props);

    this.props.retrieveList({
      listId: this.props.match.params.id,
      force: false,
    });
  }

  componentDidUpdate(oldProps: Props) {
    if (!this.props.listLoading && oldProps.listLoading) {
      this.setState({ loadingList: false });
    } else if (this.props.match.params.id !== oldProps.match.params.id) {
      this.props.retrieveList({
        listId: this.props.match.params.id,
        force: false,
      });
    }
  }

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

  renderProfileMenu() {
    if (!this.props.isAuthed) {
      return null;
    }

    let { anchorEl } = this.state;
    let { classes } = this.props;
    let isMenuOpen = !!anchorEl;

    // TODO: Get prop types working here
    // polyfill required for react-router-dom < 5.0.0
    const Link = React.forwardRef(
      (props: any, ref: React.Ref<HTMLButtonElement>) => (
        <RouterLink {...props} innerRef={ref} />
      ),
    );

    function MenuItemLink(props: MenuItemProps) {
      const { primary, to, selected, onClick } = props;

      return (
        <MenuItem
          button
          component={Link}
          to={to}
          selected={selected}
          onClick={onClick}
        >
          {primary}
        </MenuItem>
      );
    }

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
          {/* TODO: Add support for editing List name */}
          <MenuItem onClick={this.handleClose}>
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
                  userSelf.lists.map(
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
    let { classes, listLoading, userSelf } = this.props;
    let { deleted } = this.state;

    if ((!listLoading && !list) || deleted) {
      return <Redirect to="/lists" />;
    } else {
      return (
        <div className={classes.root}>
          <div className={classes.listContainer}>
            <div className={classes.listHeader}>
              <div className={classes.listNameContainer}>
                <Typography
                  component={props => (
                    <RouterLink {...props} to={'/lists/' + list.id} />
                  )}
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
              {list.things.map(item => (
                <ItemCard
                  key={item.id}
                  userSelf={userSelf}
                  item={item}
                  listContext={list}
                  itemCardVisible={false}
                  withActionButton
                  hoverDelete
                />
              ))}
            </Grid>
          </div>
          {this.renderDialog()}
        </div>
      );
    }
  }

  render() {
    let { listsById, match, userSelf } = this.props;
    let list = listsById[Number(match.params.id)];

    return !list || !userSelf
      ? this.renderLoading()
      : this.renderListDetail(list);
  }
}

const mapStateToProps: (appState: AppState) => OwnProps = appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    listLoading: appState.lists.operation.inProgress,
    listsById: appState.lists.listsById,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      retrieveList: ListRetrieveInitiated,
      deleteList: deleteList,
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
