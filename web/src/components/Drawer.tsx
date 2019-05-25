import {
  Avatar,
  Button,
  colors,
  createStyles,
  Dialog,
  DialogContent,
  DialogActions,
  DialogTitle,
  Divider,
  Drawer as DrawerUI,
  FormControl,
  FormHelperText,
  Icon,
  Input,
  InputLabel,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  LinearProgress,
  TextField,
  Typography,
  Theme,
  withStyles,
  WithStyles,
  ListItemIcon,
} from '@material-ui/core';
import * as R from 'ramda';
import {
  RouteComponentProps,
  withRouter,
  Link as RouterLink,
} from 'react-router-dom';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Dispatch, bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import {
  ListRetrieveAllInitiated,
  ListRetrieveAllPayload,
} from '../actions/lists';
import { USER_SELF_CREATE_LIST } from '../constants/user';
import { createList, UserCreateListPayload } from '../actions/user';
import { List as ListType, User } from '../types';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import classNames from 'classnames';
import { AddCircle } from '@material-ui/icons';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    avatar: {
      width: 25,
      height: 25,
      fontSize: '1em',
    },
    button: {
      margin: theme.spacing.unit,
    },
    drawer: {
      flexShrink: 0,
      zIndex: 1000,
    },
    drawerPaper: {
      zIndex: 1000,
    },
    toolbar: theme.mixins.toolbar,
    listName: {
      textDecoration: 'none',
      marginBottom: 10,
    },
    listsContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      margin: '20px 0',
      width: '100%',
    },
    margin: {
      margin: theme.spacing.unit * 2,
      marginRight: theme.spacing.unit * 3,
    },
    leftIcon: {
      marginRight: theme.spacing.unit,
    },
    iconSmall: {
      fontSize: 20,
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
  userSelf?: User;
  open: boolean;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
}

interface State {
  createDialogOpen: boolean;
  listName: string;
  nameLengthError: boolean;
  nameDuplicateError: boolean;
}

interface RouteParams {
  id: string;
  type: string;
}

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    listName: '',
    nameLengthError: false,
    nameDuplicateError: false,
  };

  componentDidMount() {
    if (!Boolean(this.props.loading)) {
      this.props.ListRetrieveAllInitiated();
    }
  }

  componentDidUpdate(oldProps: Props) {
    if (
      Boolean(oldProps.loading[USER_SELF_CREATE_LIST]) &&
      !Boolean(this.props.loading[USER_SELF_CREATE_LIST])
    ) {
      this.handleModalClose();
    }
  }

  handleModalOpen = () => {
    this.setState({ createDialogOpen: true });
  };

  handleModalClose = () => {
    this.setState({
      createDialogOpen: false,
      nameLengthError: false,
      nameDuplicateError: false,
    });
  };

  validateListName = () => {
    let { createList, userSelf } = this.props;
    let { listName } = this.state;

    // Reset error states before validation
    this.setState({ nameLengthError: false, nameDuplicateError: false });

    if (userSelf) {
      let nameExists = function(element) {
        return listName.toLowerCase() === element.name.toLowerCase();
      };

      if (listName.length === 0) {
        this.setState({ nameLengthError: true });
      } else if (userSelf.lists.some(nameExists)) {
        this.setState({ nameDuplicateError: true });
      } else {
        this.setState({ nameLengthError: false, nameDuplicateError: false });
        this.handleCreateListSubmit();
      }
    }
  };

  handleCreateListSubmit = () => {
    let { createList } = this.props;
    let { listName } = this.state;

    createList({ name: listName });
    this.handleModalClose();
  };

  renderListItems = (userList: ListType, index: number) => {
    let { listsById, classes, match } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    // TODO: Figure out what to do with the color styling for these icons
    const hue = 'blue';

    return (
      <ListItem
        button
        key={userList.id}
        component={props => <RouterLink {...props} to={`/lists/${list.id}`} />}
        // TODO: Improve logic for selection
        selected={Boolean(
          !match.params.type && Number(match.params.id) === Number(list.id),
        )}
      >
        <ListItemAvatar>
          <Avatar
            className={classes.avatar}
            style={{
              backgroundColor: colors[hue][index < 9 ? `${9 - index}00` : 100],
            }}
          >
            {userList.things.length}
          </Avatar>
        </ListItemAvatar>
        <ListItemText primary={list.name} />
      </ListItem>
    );
  };

  renderDrawer() {
    let { classes, userSelf, match, open } = this.props;
    let { createDialogOpen } = this.state;

    return (
      <DrawerUI
        open={open}
        className={classes.drawer}
        variant="persistent"
        classes={{
          paper: classes.drawerPaper,
        }}
        style={{ width: open ? 216 : 0 }}
      >
        <div className={classes.toolbar} />
        <Typography component="h6" variant="h6" className={classes.margin}>
          My Lists
        </Typography>
        <Divider />
        <Button
          variant="contained"
          color="primary"
          size="small"
          className={classes.button}
          onClick={this.handleModalOpen}
        >
          <AddCircle
            className={classNames(classes.leftIcon, classes.iconSmall)}
          />
          Create List
        </Button>
        <List>
          <ListItem
            button
            key="all"
            selected={Boolean(match.path === '/lists' && !match.params.id)}
            component={props => <RouterLink {...props} to={'/lists'} />}
          >
            <ListItemAvatar>
              <Avatar className={classes.avatar}>
                {userSelf!.lists.length}
              </Avatar>
            </ListItemAvatar>
            <ListItemText primary="All Lists" />
          </ListItem>
          {userSelf!.lists.map(this.renderListItems)}
        </List>
      </DrawerUI>
    );
  }

  renderDialog() {
    let { classes, loading } = this.props;
    let {
      createDialogOpen,
      listName,
      nameDuplicateError,
      nameLengthError,
    } = this.state;
    let isLoading = Boolean(loading[USER_SELF_CREATE_LIST]);

    return (
      <Dialog fullWidth maxWidth="xs" open={createDialogOpen}>
        <DialogTitle>Create New List</DialogTitle>
        <DialogContent>
          <FormControl style={{ width: '100%' }}>
            <TextField
              autoFocus
              margin="dense"
              id="name"
              label="Name"
              type="text"
              fullWidth
              value={listName}
              error={nameDuplicateError || nameLengthError}
              onChange={e => this.setState({ listName: e.target.value })}
            />
            <FormHelperText
              id="component-error-text"
              style={{
                display:
                  nameDuplicateError || nameLengthError ? 'block' : 'none',
              }}
            >
              {nameLengthError ? 'List name cannot be blank' : null}
              {nameDuplicateError
                ? 'You already have a list with this name'
                : null}
            </FormHelperText>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button
            disabled={isLoading}
            onClick={this.handleModalClose}
            color="primary"
          >
            Cancel
          </Button>
          <Button
            disabled={isLoading}
            onClick={this.validateListName}
            color="primary"
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  render() {
    let { loadingLists, userSelf } = this.props;

    if (userSelf || !loadingLists) {
      return (
        <React.Fragment>
          {this.renderDrawer()}
          {this.renderDialog()}
        </React.Fragment>
      );
    }
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    loadingLists: appState.lists.operation.inProgress,
    listsById: appState.lists.listsById,
    loading: appState.userSelf.loading,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListRetrieveAllInitiated,
      createList,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Drawer),
    ),
  ),
);
