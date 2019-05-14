import {
  Avatar,
  Badge,
  Button,
  createStyles,
  Dialog,
  DialogContent,
  DialogActions,
  DialogTitle,
  Divider,
  Drawer,
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
import { Link as RouterLink } from 'react-router-dom';
import React, { Component } from 'react';
import { List as ListType, Thing } from '../types';
import { Dispatch, bindActionCreators } from 'redux';
import {
  ListRetrieveAllInitiated,
  ListRetrieveAllPayload,
} from '../actions/lists';
import { USER_SELF_CREATE_LIST } from '../constants/user';
import { createList, UserCreateListPayload } from '../actions/user';
import { connect } from 'react-redux';
import { ListsByIdMap } from '../reducers/lists';
import { AppState } from '../reducers';
import withUser, { WithUserProps } from '../components/withUser';
import { Loading } from '../reducers/user';
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

/* This is just an array of colors I grabbed from a famous picasso painting.  Testing how the lists could look with a random color identifier. */
const colorArray = [
  '#90bab0',
  '#5b3648',
  '#2b5875',
  '#ab6c5d',
  '#b16f7b',
  '#764b45',
  '#53536d',
  '#7adbd0',
  '#32cad7',
  '#17bfe3',
  '#c2e9e6',
  '#978fb6',
  '#04256a',
  '#3eb1b6',
  '#7266b8',
  '#1172d0',
  '#ed0000',
  '#abae77',
  '#73b06d',
  '#d7799b',
  '#5b7e7a',
  '#6fc16d',
  '#8c8c58',
  '#d8070d',
  '#ca8866',
  '#d9e4e0',
  '#c17b9f',
  '#7eb691',
  '#71dee1',
  '#50bc45',
  '#904317',
  '#292234',
  '#a64e38',
  '#c5c3d1',
  '#825e6a',
  '#234282',
  '#30705f',
  '#be2d00',
  '#8cac23',
  '#9b708b',
  '#6c703d',
  '#c09f12',
  '#265e97',
  '#d21b39',
  '#948c5b',
  '#6d6536',
  '#778588',
  '#c2350a',
  '#5ea6b4',
];

interface OwnProps extends WithStyles<typeof styles>{
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
}

interface State {
  createDialogOpen: boolean;
  listName: string;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

class DrawerUI extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    listName: '',
  };

  componentDidMount() {
    this.props.ListRetrieveAllInitiated();
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
    let { listsById, classes } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    let randomItem = colorArray[Math.floor(Math.random()*colorArray.length)]

    return (
      <ListItem button key={userList.id}
      component={props => <RouterLink {...props} to={'/lists/' + list.id} />}>
        <ListItemAvatar>
          <Avatar
            className={classes.avatar}
            style={{backgroundColor: randomItem}}
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
    if (
      this.props.retrievingUser ||
      !this.props.userSelf ||
      this.props.loadingLists
    ) {
      return this.renderLoading();
    } else {
      let { classes, userSelf } = this.props;
      let isLoading = Boolean(this.props.loading[USER_SELF_CREATE_LIST]);
      return (
        <React.Fragment>
          <Drawer
            className={classes.drawer}
            variant="permanent"
            classes={{
              paper: classes.drawerPaper,
            }}
          >
            <div className={classes.toolbar} />
            <Typography
              component="h4"
              variant="h4"
              style={{margin: 16}}
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
              >
                <ListItemAvatar>
                  <Icon color="action">create</Icon>
                </ListItemAvatar>
                <ListItemText
                  primary='Create New List'/>
              </ListItem>
              <ListItem button key='all' selected={true}>
                <ListItemAvatar>
                  <Avatar className={classes.avatar}>{userSelf.lists.length}</Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary='All Lists'/>
              </ListItem>
              {userSelf.lists.map(this.renderListItems)}
            </List>
          </Drawer>
          <div className={classes.layout}>
            <div>
              <Dialog
                fullWidth
                maxWidth="xs"
                open={this.state.createDialogOpen}
              >
                <DialogTitle>Create a List</DialogTitle>
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
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(DrawerUI),
  ),
);
