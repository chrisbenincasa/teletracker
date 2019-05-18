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
  Icon,
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
} from '@material-ui/core';
import * as R from 'ramda';
import { RouteComponentProps, withRouter, Link as RouterLink } from 'react-router-dom';
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

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    avatar: {
      width: 25,
      height: 25,
      fontSize: '1em',
    },
    drawer: {
      width: 240,
      flexShrink: 0,
      zIndex: 1000,
    },
    drawerPaper: {
      width: 240,
    },
    toolbar: theme.mixins.toolbar,
    listName: {
      textDecoration: 'none',
      marginBottom: 10
    },
    listsContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      margin: '20px 0',
      width: '100%'
    },
    margin: {
      margin: theme.spacing.unit * 2,
      marginRight: theme.spacing.unit * 3,
    },
  });

interface OwnProps extends WithStyles<typeof styles>{
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
  userSelf?: User;
}

interface DrawerProps {
  open?: boolean;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
}

interface State {
  createDialogOpen: boolean;
  listName: string;
}
interface RouteParams {
  id: string;
  type: string;
}

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps &
  DrawerProps;

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    listName: '',
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

  refreshUser = () => {
    this.props.retrieveUser(true);
  };

  handleModalOpen = () => {
    this.setState({ createDialogOpen: true });
  };

  handleModalClose = () => {
    this.setState({ createDialogOpen: false });
  };

  handleCreateListSubmit = () => {
    if (this.state.listName.length > 0) {
      this.props.createList({ name: this.state.listName });
    }
  };

  renderListItems = (userList: ListType, index: number) => {
    let { listsById, classes, match } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    const hue = 'blue';

    return (
      <ListItem
        button
        key={userList.id}
        component={props =>
          <RouterLink {...props} to={'/lists/' + list.id} />
        }
        // This is a little hacky and can be improved
        selected={Boolean(!match.params.type && Number(match.params.id) === Number(list.id))}
      >
        <ListItemAvatar>
          <Avatar
            className={classes.avatar}
            style={{backgroundColor: colors[hue][index < 9 ? `${9 - index}00` : 100]}}
          >
            {userList.things.length}
          </Avatar>
          </ListItemAvatar>
        <ListItemText
          primary={list.name} />
      </ListItem>
    )
  }

  renderLoading() {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  render() {
    let { match, open } = this.props;

    if (
      !this.props.userSelf ||
      this.props.loadingLists
    ) {
      return this.renderLoading();
    } else {
      let { classes, userSelf } = this.props;
      let isLoading = Boolean(this.props.loading[USER_SELF_CREATE_LIST]);
      return (
        <React.Fragment>
          <DrawerUI
            open={open}
            className={classes.drawer}
            {...this.props}
            variant="permanent"
            classes={{
              paper: classes.drawerPaper,
            }}
          >
            <div className={classes.toolbar} />
            <Typography
              component="h4"
              variant="h4"
              className={classes.margin}
            >
              My Lists
              <Button onClick={this.refreshUser}>
                <Icon color="action">refresh</Icon>
              </Button>
            </Typography>
            <Divider />
            <List>
              <ListItem
                button
                key='create'
                onClick={this.handleModalOpen}
                selected={this.state.createDialogOpen}
              >
                <ListItemAvatar>
                  <Icon color="action">create</Icon>
                </ListItemAvatar>
                <ListItemText
                  primary='Create New List'/>
              </ListItem>
              <ListItem
                button
                key='all'
                selected={Boolean(!match.params.type && !match.params.id)}
                component={props =>
                  <RouterLink {...props} to={'/lists'} />
                }
              >
                <ListItemAvatar>
                  <Avatar className={classes.avatar}>{userSelf.lists.length}</Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary='All Lists'/>
              </ListItem>
              {userSelf.lists.map(this.renderListItems)}
            </List>
          </DrawerUI>
          <div>
            <Dialog
              fullWidth
              maxWidth="xs"
              open={this.state.createDialogOpen}
            >
              <DialogTitle>Create New List</DialogTitle>
              <DialogContent>
                <TextField
                  autoFocus
                  margin="dense"
                  id="name"
                  label="Name"
                  type="text"
                  fullWidth
                  value={this.state.listName}
                  onChange={e => this.setState({ listName: e.target.value })}
                />
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
                  onClick={this.handleCreateListSubmit}
                  color="primary"
                >
                  Create
                </Button>
              </DialogActions>
            </Dialog>
          </div>
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
