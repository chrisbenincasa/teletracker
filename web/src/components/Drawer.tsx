import React, { Component, RefObject } from 'react';
import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  Divider,
  Drawer as DrawerUI,
  List,
  ListItem,
  ListItemAvatar,
  ListItemIcon,
  ListItemText,
  Theme,
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
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { List as ListType } from '../types';
import AuthDialog from './Auth/AuthDialog';
import withRouter, { WithRouterProps } from 'next/dist/client/with-router';
import Link from 'next/link';
import RouterLink from './RouterLink';

export const DrawerWidthPx = 220;

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
    toolbar: theme.mixins.toolbar,
    list: {
      padding: theme.spacing(0, 1),
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
  primary: string;
  selected: boolean;
  to: string;
  as?: string;
  onClick?: () => void;
}

interface ListItemProps {
  to: string;
  primary?: string;
  selected?: boolean;
  onClick?: () => void;
}

// TODO: Get type definitions for props working
const ListItemLink = withStyles(styles, { withTheme: true })(
  (props: LinkProps & WithStyles<typeof styles, true>) => {
    const { index, primary, selected, listLength } = props;

    const backgroundColor =
      props.theme.palette.primary[
        index ? (index < 9 ? `${9 - index}00` : 100) : 900
      ];

    const handleClick = () => {
      if (props.onClick) {
        props.onClick();
      }
    };

    const ButtonLink = React.forwardRef((props: any, ref) => {
      let { onClick, href } = props;
      return (
        <Link href={href} passHref>
          <a
            onClick={onClick}
            ref={ref as RefObject<HTMLAnchorElement>}
            {...props}
          >
            {primary}
          </a>
        </Link>
      );
    });

    return (
      <ListItem
        button
        // onClick={handleClick}
        // href={props.to}
        // component={ButtonLink}
        selected={selected}
      >
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
          <ListItemText primary={primary} />
          {/* <a onClick={handleClick}>
          </a> */}
        </Link>
      </ListItem>
    );
  },
);

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    authModalOpen: false,
    authModalScreen: 'login',
    listsLoadedOnce: false,
  };

  componentDidMount() {
    console.log('Drawer mounted');
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
    });
  };

  renderListItems = (userList: ListType, index: number) => {
    let { listsById } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    const listPath = `/lists/${list.id}`;
    return (
      <ListItemLink
        index={index}
        key={userList.id}
        to={`/lists/[id]?id=${list.id}`}
        as={`/lists/${list.id}`}
        selected={listPath === this.props.router.pathname}
        primary={list.name}
        listLength={userList.totalItems}
        onClick={this.props.closeRequested}
      />
    );
  };

  renderDrawerContents() {
    let { classes, isAuthed, listsById } = this.props;

    function ListItemLink(props: ListItemProps) {
      const { primary, to, selected } = props;

      return (
        <Link href={to}>
          {/*<a>*/}
          <ListItem button selected={selected} onClick={props.onClick}>
            <ListItemIcon>
              <Settings />
            </ListItemIcon>
            {primary}
          </ListItem>
          {/*</a>*/}
        </Link>
      );
    }

    return (
      <React.Fragment>
        <div className={classes.toolbar} />
        {['xs', 'sm', 'md'].includes(this.props.width) && (
          <React.Fragment>
            <List>
              <ListItem
                button
                component={RouterLink}
                href="/all"
                selected={location.pathname.toLowerCase() === '/all'}
              >
                <ListItemIcon>
                  <Apps />
                </ListItemIcon>
                Explore
              </ListItem>
              <ListItem
                button
                component={RouterLink}
                href="/popular"
                selected={location.pathname.toLowerCase() === '/popular'}
              >
                <ListItemIcon>
                  <TrendingUp />
                </ListItemIcon>
                Browse Popular
              </ListItem>
              <ListItem
                button
                component={RouterLink}
                href="/new"
                selected={location.pathname.toLowerCase() === '/new'}
              >
                <ListItemIcon>
                  <FiberNew />
                </ListItemIcon>
                What's New?
              </ListItem>
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
              <Button
                variant="contained"
                color="primary"
                size="small"
                onClick={this.handleModalOpen}
                fullWidth
              >
                <AddCircle
                  className={classNames(classes.leftIcon, classes.iconSmall)}
                />
                Create List
              </Button>
              {_.map(listsById, this.renderListItems)}
            </List>
            <Divider />
          </React.Fragment>
        ) : null}
        <List>
          {isAuthed ? (
            <React.Fragment>
              <ListItemLink
                to="/account"
                primary="Settings"
                onClick={this.props.closeRequested}
              />
              <ListItem button onClick={this.handleLogout}>
                <ListItemIcon>
                  <PowerSettingsNew />
                </ListItemIcon>
                Logout
              </ListItem>
            </React.Fragment>
          ) : (
            <React.Fragment>
              <ListItem button onClick={() => this.toggleAuthModal('login')}>
                <ListItemIcon>
                  <Lock />
                </ListItemIcon>
                Login
              </ListItem>
              <ListItem button onClick={() => this.toggleAuthModal('signup')}>
                <ListItemIcon>
                  <PersonAdd />
                </ListItemIcon>
                Signup
              </ListItem>
            </React.Fragment>
          )}
        </List>
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
