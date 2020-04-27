import React, { Component, ReactElement, ReactNode, RefObject } from 'react';
import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  Divider,
  Drawer as DrawerUI,
  List,
  ListItem,
  ListItemProps as MuiListItemProps,
  ListItemAvatar,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  IconButton,
  Theme,
  Tooltip,
  Typography,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
import {
  AddCircle,
  Lock,
  PersonAdd,
  PowerSettingsNew,
  Settings,
  TrendingUp,
  Apps,
  FiberNew,
  OfflineBolt,
} from '@material-ui/icons';
import classNames from 'classnames';
import _ from 'lodash';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../actions/auth';
import {
  LIST_RETRIEVE_ALL_INITIATED,
  ListRetrieveAllPayload,
  retrieveAllLists,
} from '../actions/lists';
import CreateListDialog from './Dialogs/CreateListDialog';
import SmartListDialog from './Dialogs/SmartListDialog';
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { List as ListType } from '../types';
import AuthDialog from './Auth/AuthDialog';
import withRouter, { WithRouterProps } from 'next/dist/client/with-router';
import Link from 'next/link';
import RouterLink from './RouterLink';

// TODO: Adapt to screen size
export const DrawerWidthPx = 250;

const styles = (theme: Theme) =>
  createStyles({
    avatar: {
      width: 30,
      height: 30,
      fontSize: '1em',
    },
    drawer: {
      flexShrink: 0,
      width: DrawerWidthPx,
      zIndex: `${theme.zIndex.appBar - 1} !important` as any,
    },
    fixedListItems: {
      width: '100%',
      padding: theme.spacing(0, 1),
      flex: '0 0 auto',
    },
    toolbar: theme.mixins.toolbar,
    list: {
      padding: theme.spacing(0, 1),
      flex: '1 1 auto',
      overflowY: 'scroll',
    },
    listName: {
      textDecoration: 'none',
      marginBottom: theme.spacing(1),
    },
    listsContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      margin: theme.spacing(3, 0),
      width: '100%',
    },
    margin: {
      margin: theme.spacing(2, 3, 2),
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
  isLoggingIn: boolean;
  closeRequested: () => void;
}

interface InjectedProps {
  isAuthed: boolean;
}

interface DispatchProps {
  retrieveAllLists: (payload?: ListRetrieveAllPayload) => void;
  logout: () => void;
}

interface State {
  createDialogOpen: boolean;
  smartListDialogOpen: boolean;
  authModalOpen: boolean;
  authModalScreen?: 'login' | 'signup';
  listsLoadedOnce: boolean;
}

interface RouteParams {
  id: string;
  type: string;
}

interface WidthProps {
  width: string;
}

type Props = OwnProps &
  WithRouterProps &
  DispatchProps &
  WithStyles<typeof styles> &
  InjectedProps &
  WidthProps;

interface LinkProps {
  index?: number;
  key: number | string;
  listLength: number;
  dynamic?: boolean;
  primary: string;
  selected: boolean;
  to: string;
  as?: string;
  onClick?: () => void;
  onSmartListClick: () => void;
}

interface ListItemProps {
  to: string;
  primary?: string;
  selected?: boolean;
  onClick?: () => void;
  icon?: ReactElement;
  ListItemProps?: MuiListItemProps;
}

// TODO: Get type definitions for props working
const DrawerItemListLink = withStyles(styles, { withTheme: true })(
  (props: LinkProps & WithStyles<typeof styles, true>) => {
    const { index, primary, selected, listLength, dynamic } = props;

    const backgroundColor =
      props.theme.palette.primary[
        index ? (index < 9 ? `${9 - index}00` : 100) : 900
      ];

    const handleClick = () => {
      if (props.onClick) {
        props.onClick();
      }
    };

    return (
      <ListItem button onClick={handleClick} selected={selected}>
        <ListItemAvatar>
          <Avatar
            style={{
              backgroundColor,
              width: 30,
              height: 30,
              fontSize: '1em',
            }}
          >
            {listLength >= 100 ? '99+' : listLength}
          </Avatar>
        </ListItemAvatar>
        <Link href={props.to} as={props.as} passHref>
          <ListItemText
            style={{
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            primary={primary}
          />
        </Link>
        {dynamic && (
          <Tooltip title={'This is a Smart List'} placement={'right'}>
            <ListItemSecondaryAction>
              <IconButton
                edge="end"
                aria-label="smart lists"
                size="small"
                onClick={() => props.onSmartListClick()}
              >
                <OfflineBolt />
              </IconButton>
            </ListItemSecondaryAction>
          </Tooltip>
        )}
      </ListItem>
    );
  },
);

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    smartListDialogOpen: false,
    authModalOpen: false,
    authModalScreen: 'login',
    listsLoadedOnce: false,
  };

  componentDidMount() {
    if (this.props.isAuthed) {
      this.props.retrieveAllLists({ includeThings: false });
    }
  }

  componentDidUpdate(oldProps: Props) {
    if (oldProps.isLoggingIn && !this.props.isLoggingIn) {
      this.toggleAuthModal('login');
    }

    if (!oldProps.isAuthed && this.props.isAuthed) {
      this.props.retrieveAllLists({ includeThings: false });
    }

    if (
      !this.state.listsLoadedOnce &&
      oldProps.loadingLists &&
      !this.props.loadingLists
    ) {
      this.setState({
        listsLoadedOnce: true,
      });
    }
  }

  handleLogout = () => {
    this.props.logout();
    this.props.closeRequested();
  };

  navigateSettings = () => {
    this.props.closeRequested();
  };

  toggleAuthModal = (initialForm?: 'login' | 'signup') => {
    if (['xs', 'sm', 'md'].includes(this.props.width)) {
      this.setState({
        authModalOpen: false,
        authModalScreen: undefined,
      });
      this.props.router.push(`/${initialForm}`);
    } else {
      this.setState({
        authModalOpen: !this.state.authModalOpen,
        authModalScreen: initialForm,
      });
    }
  };

  handleModalOpen = () => {
    if (this.props.isAuthed) {
      this.setState({ createDialogOpen: true });
    } else {
      this.toggleAuthModal('login');
    }
  };

  handleModalClose = () => {
    this.setState({
      createDialogOpen: false,
      smartListDialogOpen: false,
    });
  };

  renderListItems = (userList: ListType, index: number) => {
    let { listsById } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    const listPath = `/lists/${list.id}`;

    return (
      <DrawerItemListLink
        index={index}
        key={userList.id}
        to={`/lists/[id]?id=${list.id}`}
        as={`/lists/${list.id}`}
        selected={listPath === this.props.router.pathname}
        primary={list.name}
        dynamic={list.isDynamic}
        listLength={userList.totalItems}
        onClick={this.props.closeRequested}
        onSmartListClick={() => this.setState({ smartListDialogOpen: true })}
      />
    );
  };

  renderDrawerContents() {
    let { classes, isAuthed, listsById } = this.props;

    const sortedLists = _.sortBy(
      _.values(listsById),
      list => (list.createdAt ? -new Date(list.createdAt) : null),
      list => (list.legacyId ? -list.legacyId : null),
      'id',
    );

    function ListItemLink(props: ListItemProps) {
      const { primary, to, selected, icon } = props;

      return (
        <Link href={to}>
          <ListItem button selected={selected} onClick={props.onClick}>
            {icon ? <ListItemIcon>{icon}</ListItemIcon> : null}
            <ListItemText>{primary}</ListItemText>
          </ListItem>
        </Link>
      );
    }

    return (
      <React.Fragment>
        <div className={classes.toolbar} />
        {['xs', 'sm', 'md'].includes(this.props.width) && (
          <React.Fragment>
            <List>
              <ListItemLink
                to="/popular"
                primary="Explore"
                icon={<TrendingUp />}
                ListItemProps={{
                  selected: location.pathname.toLowerCase() === '/popular',
                  button: true,
                }}
              />
              <ListItemLink
                to="/new"
                primary="What's New?"
                icon={<FiberNew />}
                ListItemProps={{
                  selected: location.pathname.toLowerCase() === '/new',
                  button: true,
                }}
              />
            </List>
            <Divider />
          </React.Fragment>
        )}
        {isAuthed ? (
          <React.Fragment>
            <Typography component="h6" variant="h6" className={classes.margin}>
              My Lists
            </Typography>
            <List className={classes.list}>
              {_.map(sortedLists, this.renderListItems)}
              <Button
                variant="contained"
                size="small"
                onClick={this.handleModalOpen}
                fullWidth
              >
                <AddCircle
                  className={classNames(classes.leftIcon, classes.iconSmall)}
                />
                Create List
              </Button>
            </List>
            <Divider />
          </React.Fragment>
        ) : null}

        {isAuthed ? (
          <List className={classes.fixedListItems}>
            <ListItemLink
              to="/account"
              primary="Settings"
              onClick={this.props.closeRequested}
              icon={<Settings />}
            />
            <ListItem button onClick={this.handleLogout}>
              <ListItemIcon>
                <PowerSettingsNew />
              </ListItemIcon>
              <ListItemText>Logout</ListItemText>
            </ListItem>
          </List>
        ) : (
          <List>
            <ListItem button onClick={() => this.toggleAuthModal('login')}>
              <ListItemIcon>
                <Lock />
              </ListItemIcon>
              <ListItemText>Login</ListItemText>
            </ListItem>
            <ListItem button onClick={() => this.toggleAuthModal('signup')}>
              <ListItemIcon>
                <PersonAdd />
              </ListItemIcon>
              <ListItemText>Signup</ListItemText>
            </ListItem>
          </List>
        )}
      </React.Fragment>
    );
  }

  renderDrawer() {
    let { classes, open } = this.props;

    return (
      <DrawerUI
        open={open}
        anchor="left"
        className={classes.drawer}
        style={{ width: open ? 220 : 0 }}
        ModalProps={{
          onBackdropClick: this.props.closeRequested,
          onEscapeKeyDown: this.props.closeRequested,
        }}
        PaperProps={{
          style: { width: DrawerWidthPx },
        }}
      >
        {this.isLoading() ? <CircularProgress /> : this.renderDrawerContents()}
      </DrawerUI>
    );
  }

  isLoading() {
    return this.props.loadingLists && !this.state.listsLoadedOnce;
  }

  render() {
    return (
      <React.Fragment>
        {this.renderDrawer()}
        <CreateListDialog
          open={this.state.createDialogOpen}
          onClose={this.handleModalClose.bind(this)}
        />
        <SmartListDialog
          open={this.state.smartListDialogOpen}
          onClose={this.handleModalClose.bind(this)}
        />
        <AuthDialog
          open={this.state.authModalOpen}
          onClose={() => this.toggleAuthModal()}
          initialForm={this.state.authModalScreen}
        />
      </React.Fragment>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !!appState.auth.token,
    loadingLists:
      appState.lists.operation.inProgress &&
      appState.lists.operation.operationType === LIST_RETRIEVE_ALL_INITIATED,
    listsById: appState.lists.listsById,
    loading: appState.userSelf.loading,
    isLoggingIn: appState.auth.isLoggingIn,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      logout,
      retrieveAllLists,
    },
    dispatch,
  );

export default withWidth()(
  withStyles(styles, { withTheme: true })(
    withRouter(connect(mapStateToProps, mapDispatchToProps)(Drawer)),
  ),
);
