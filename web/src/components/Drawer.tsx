import {
  Avatar,
  Button,
  CircularProgress,
  colors,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer as DrawerUI,
  FormControl,
  FormHelperText,
  List,
  ListItem,
  ListItemAvatar,
  ListItemIcon,
  ListItemText,
  TextField,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { AddCircle, PowerSettingsNew, Settings } from '@material-ui/icons';
import classNames from 'classnames';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../actions/auth';
import {
  createList,
  LIST_RETRIEVE_ALL_INITIATED,
  ListRetrieveAllPayload,
  retrieveAllLists,
  USER_SELF_CREATE_LIST,
  UserCreateListPayload,
} from '../actions/lists';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { layoutStyles } from '../styles';
import { List as ListType } from '../types';

export const DrawerWidthPx = 220;

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    avatar: {
      width: 25,
      height: 25,
      fontSize: '1em',
    },
    button: {
      margin: theme.spacing(1),
    },
    drawer: {
      flexShrink: 0,
      zIndex: 1000,
    },
    drawerPaper: {
      zIndex: 1000,
      width: DrawerWidthPx,
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
      margin: theme.spacing(2),
      marginRight: theme.spacing(3),
    },
    leftIcon: {
      marginRight: theme.spacing(1),
    },
    iconSmall: {
      fontSize: 20,
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
  open: boolean;
}

interface DispatchProps {
  retrieveAllLists: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
  logout: () => void;
}

interface State {
  createDialogOpen: boolean;
  listName: string;
  nameLengthError: boolean;
  nameDuplicateError: boolean;
  loadingLists: boolean;
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

interface LinkProps {
  index?: number;
  key: number | string;
  listLength: number;
  primary: string;
  selected: boolean;
  to: string;
}

interface ListItemProps {
  to: any;
  primary?: string;
  selected?: any;
}

// TODO: Get type definitions for props working
class ListItemLink extends React.Component<LinkProps, {}> {
  renderLink = React.forwardRef(
    (itemProps: any, ref: React.Ref<HTMLButtonElement>) => (
      <RouterLink to={this.props.to} {...itemProps} ref={ref} />
    ),
  );

  render() {
    const { index, primary, selected, listLength } = this.props;
    const hue = 'blue';
    return (
      <ListItem button component={this.renderLink} selected={selected}>
        <ListItemAvatar>
          <Avatar
            style={{
              backgroundColor:
                colors[hue][index ? (index < 9 ? `${9 - index}00` : 100) : 900],
              width: 25,
              height: 25,
              fontSize: '1em',
            }}
          >
            {listLength}
          </Avatar>
        </ListItemAvatar>
        <ListItemText primary={primary} />
      </ListItem>
    );
  }
}

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    listName: '',
    nameLengthError: false,
    nameDuplicateError: false,
    loadingLists: true,
  };

  componentDidMount() {
    if (!Boolean(this.props.loading)) {
    }
    this.props.retrieveAllLists({ includeThings: false });
  }

  componentDidUpdate(oldProps: Props) {
    if (
      Boolean(oldProps.loading[USER_SELF_CREATE_LIST]) &&
      !Boolean(this.props.loading[USER_SELF_CREATE_LIST])
    ) {
      this.handleModalClose();
    }

    if (Boolean(oldProps.loadingLists) && !Boolean(this.props.loadingLists)) {
      this.setState({ loadingLists: false });
    }
  }

  handleLogout = () => {
    this.props.logout();
  };

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
    let { listsById, userSelf } = this.props;
    let { listName } = this.state;

    // Reset error states before validation
    this.setState({ nameLengthError: false, nameDuplicateError: false });

    if (userSelf) {
      let nameExists = function(element) {
        return listName.toLowerCase() === element.name.toLowerCase();
      };

      if (listName.length === 0) {
        this.setState({ nameLengthError: true });
      } else if (R.values(listsById).some(nameExists)) {
        this.setState({ nameDuplicateError: true });
      } else {
        this.setState({
          nameLengthError: false,
          nameDuplicateError: false,
        });
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
      <ListItemLink
        index={index}
        key={userList.id}
        to={`/lists/${list.id}`}
        // TODO: Improve logic for selection
        selected={Boolean(
          !match.params.type && Number(match.params.id) === Number(list.id),
        )}
        primary={list.name}
        listLength={userList.thingCount}
      />
    );
  };

  renderDrawerContents() {
    let { classes, listsById } = this.props;

    // TODO: Get prop types working here
    // polyfill required for react-router-dom < 5.0.0
    const Link = React.forwardRef(
      (props: any, ref: React.Ref<HTMLButtonElement>) => (
        <RouterLink {...props} innerRef={ref} />
      ),
    );

    function ListItemLink(props: ListItemProps) {
      const { primary, to, selected } = props;

      return (
        <ListItem button component={Link} to={to} selected={selected}>
          <ListItemIcon>
            <Settings />
          </ListItemIcon>
          {primary}
        </ListItem>
      );
    }

    return (
      <React.Fragment>
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
        <List>{_.map(listsById, this.renderListItems)}</List>
        <List>
          <Divider />
          <ListItemLink to="/account" primary="Settings" />
          <ListItem button onClick={this.handleLogout}>
            <ListItemIcon>
              <PowerSettingsNew />
            </ListItemIcon>
            Logout
          </ListItem>
        </List>
      </React.Fragment>
    );
  }

  renderDrawer() {
    let { classes, open, listsById } = this.props;

    return (
      <DrawerUI
        open={open}
        anchor="left"
        className={classes.drawer}
        variant="persistent"
        classes={{
          paper: classes.drawerPaper,
        }}
        style={{ width: open ? 216 : 0 }}
      >
        {this.isLoading() ? <CircularProgress /> : this.renderDrawerContents()}
      </DrawerUI>
    );
  }

  renderDialog() {
    let { loading } = this.props;
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
    return (
      <React.Fragment>
        {this.renderDrawer()}
        {this.renderDialog()}
      </React.Fragment>
    );
  }

  isLoading() {
    return (
      this.state.loadingLists || this.props.loadingLists || !this.props.userSelf
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    loadingLists:
      appState.lists.operation.inProgress &&
      appState.lists.operation.operationType == LIST_RETRIEVE_ALL_INITIATED,
    listsById: appState.lists.listsById,
    loading: appState.userSelf.loading,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      createList,
      logout,
      retrieveAllLists,
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
  () => null,
);
