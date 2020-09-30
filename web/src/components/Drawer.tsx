import React, { ReactElement, useCallback, useEffect, useState } from 'react';
import {
  Avatar,
  Badge,
  Button,
  CircularProgress,
  Collapse,
  createStyles,
  Divider,
  Drawer as DrawerUI,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  ListSubheader,
  makeStyles,
  SwipeableDrawer,
  Theme,
  Tooltip,
  Typography,
} from '@material-ui/core';
import {
  AddCircle,
  ExpandLess,
  ExpandMore,
  FiberNew,
  Lock,
  MovieFilter,
  PersonAdd,
  PowerSettingsNew,
  Public,
  Settings,
  TrendingUp,
  Label,
  List as ListIcon,
} from '@material-ui/icons';
import CreateListDialog from './Dialogs/CreateListDialog';
import SmartListDialog from './Dialogs/SmartListDialog';
import PublicListDialog from './Dialogs/PublicListDialog';
import AuthDialog from './Auth/AuthDialog';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { List as ListType } from '../types';
import _ from 'lodash';
import Link from 'next/link';
import { useWidth } from '../hooks/useWidth';
import useIsMobile from '../hooks/useIsMobile';
import { useRouter } from 'next/router';
import { useWithUserContext } from '../hooks/useWithUser';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import selectMyLists from '../selectors/selectMyLists';
import { ListItemProps as MuiListItemProps } from '@material-ui/core/ListItem/ListItem';
import {
  useDispatchAction,
  useDispatchSideEffect,
} from '../hooks/useDispatchAction';
import { logout } from '../actions/auth';
import {
  LIST_RETRIEVE_ALL_INITIATED,
  retrieveAllLists,
} from '../actions/lists';
import { usePrevious } from '../hooks/usePrevious';

export const DrawerWidthPx = 300;

const useStyles = makeStyles((theme: Theme) =>
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
    drawerIcon: {
      width: 30,
      height: 30,
    },
    fixedListItems: {
      width: '100%',
      padding: theme.spacing(0, 1),
      flex: '0 0 auto',
    },
    parent: {
      paddingLeft: theme.spacing(2),
    },
    nested: {
      paddingLeft: theme.spacing(3),
    },
    // toolbar: theme.mixins.toolbar,
    toolbar: {
      minHeight: 48, // We're using dense toolbar... make a mixin if other pages need this
    },
    list: {
      padding: theme.spacing(1),
      flex: '1 1 auto',
      overflowY: 'scroll',
    },
    listHeader: {
      background: theme.palette.background.paper,
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
    newList: {
      margin: theme.spacing(2, 0),
    },
  }),
);

interface ListItemProps {
  readonly to: string;
  readonly primary?: string;
  readonly selected?: boolean;
  readonly onClick?: () => void;
  readonly icon?: ReactElement;
  readonly ListItemProps?: MuiListItemProps;
}

interface LinkProps {
  readonly index?: number;
  readonly key: number | string;
  readonly listLength: number;
  readonly isDynamic?: boolean;
  readonly isPublic?: boolean;
  readonly primary: string;
  readonly selected: boolean;
  readonly to: string;
  readonly as?: string;
  readonly onClick?: () => void;
  readonly onSmartListClick: () => void;
  readonly onPublicListClick: () => void;
}

interface Props {
  readonly open: boolean;
  readonly closeRequested: () => void;
  readonly drawerStateChanged: (open: boolean) => void;
}

const DrawerItemListLink = (props: LinkProps) => {
  const { primary, selected, listLength, isDynamic, isPublic } = props;
  const classes = useStyles();

  const handleClick = () => {
    if (props.onClick) {
      props.onClick();
    }
  };

  return (
    <Link href={props.to} as={props.as} passHref>
      <ListItem
        button
        onClick={handleClick}
        selected={selected}
        className={classes.nested}
        dense
        disableGutters
      >
        {isPublic && (
          <ListItemIcon>
            <Public style={{ width: 24, height: 24 }} />
          </ListItemIcon>
        )}

        <ListItemText
          style={{
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
          primary={primary}
        />
        <ListItemSecondaryAction>
          {_.isUndefined(listLength)
            ? 0
            : listLength >= 100
            ? '99+'
            : listLength}
        </ListItemSecondaryAction>
      </ListItem>
    </Link>
  );
};

export default function Drawer(props: Props) {
  const classes = useStyles();
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [smartListDialogOpen, setSmartListDialogOpen] = useState(false);
  const [publicListDialogOpen, setPublicListDialogOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authModalScreen, setAuthModalScreen] = useState<
    'login' | 'signup' | undefined
  >('login');
  const lists = useStateSelector(selectMyLists);
  const [listsLoadedOnce, setListsLoadedOnce] = useState(false);

  const { isLoggedIn } = useWithUserContext();
  const wasLoggedIn = usePrevious(isLoggedIn);
  const [isLoggingIn, wasLoggingIn] = useStateSelectorWithPrevious(
    state => state.auth.isLoggingIn,
  );

  const [loadingLists, wasLoadingLists] = useStateSelectorWithPrevious(
    state => state.lists.loading[LIST_RETRIEVE_ALL_INITIATED],
  );

  const [isOpen, setIsOpen] = useState(false);
  const [manualListOpen, setManualListOpen] = React.useState(true);
  const [smartListOpen, setSmartListOpen] = React.useState(false);

  const handleManualListsClick = () => {
    setManualListOpen(!manualListOpen);
    setSmartListOpen(false);
  };

  const handleSmartListsClick = () => {
    setSmartListOpen(!smartListOpen);
    setManualListOpen(false);
  };

  const iOS =
    (process as any).browser &&
    navigator &&
    /iPad|iPhone|iPod/.test(navigator.userAgent);

  useEffect(() => {
    setIsOpen(props.open);
  }, [props.open]);

  const setDrawerState = useCallback(
    (value: boolean) => {
      if (isOpen !== value) {
        setIsOpen(value);
        props.drawerStateChanged(value);
      }
    },
    [isOpen, props.drawerStateChanged],
  );

  const width = useWidth();
  const isMobile = useIsMobile();
  const router = useRouter();

  const dispatchLogout = useDispatchSideEffect(logout);
  const dispatchRetrieveAllLists = useDispatchAction(retrieveAllLists);

  useEffect(() => {
    if (isLoggedIn) {
      dispatchRetrieveAllLists({ includeThings: false });
    }
  }, []);

  useEffect(() => {
    if (wasLoggingIn && !isLoggingIn) {
      toggleAuthModal('login');
    }
  }, [isLoggingIn]);

  useEffect(() => {
    // Extra check for undefined here because we don't want this to fire on mount
    // as usePrevious returned undefined on the first render
    if (
      !_.isUndefined(wasLoggedIn) &&
      !wasLoggedIn &&
      isLoggedIn &&
      !loadingLists
    ) {
      dispatchRetrieveAllLists({ includeThings: false });
    }
  }, [isLoggedIn, loadingLists]);

  useEffect(() => {
    if (!listsLoadedOnce && wasLoadingLists && !loadingLists) {
      setListsLoadedOnce(true);
    }
  }, [listsLoadedOnce, loadingLists]);

  const handleLogout = useCallback(() => {
    dispatchLogout();
    props.closeRequested();
  }, []);

  const navigateSettings = () => {
    props.closeRequested();
  };

  const toggleAuthModal = (initialForm?: 'login' | 'signup') => {
    if (isMobile) {
      setAuthModalOpen(false);
      setAuthModalScreen(undefined);
      router.push(`/${initialForm}`);
    } else {
      setAuthModalOpen(prev => !prev);
      setAuthModalScreen(initialForm);
    }
  };

  const toggleLoginModal = useCallback(() => {
    toggleAuthModal('login');
  }, []);

  const toggleSignupModal = useCallback(() => {
    toggleAuthModal('signup');
  }, []);

  const handleModalOpen = useCallback(() => {
    if (isLoggedIn) {
      setCreateDialogOpen(true);
    } else {
      toggleAuthModal('login');
    }
  }, [isLoggedIn]);

  const handleModalClose = useCallback(() => {
    setCreateDialogOpen(false);
    setSmartListDialogOpen(false);
    setPublicListDialogOpen(false);
  }, []);

  const openSmartListDialog = useCallback(() => {
    setSmartListDialogOpen(true);
  }, []);

  const openPublicListDialog = useCallback(() => {
    setPublicListDialogOpen(true);
  }, []);

  const renderListItems = (userList: ListType, index: number) => {
    let listWithDetails = lists[userList.id];
    let list = listWithDetails || userList;
    const listPath = `/lists/${list.id}`;

    return (
      <DrawerItemListLink
        index={index}
        key={userList.id}
        to={`/lists/[id]?id=${list.id}`}
        as={`/lists/${list.id}`}
        selected={listPath === router.pathname}
        primary={list.name}
        isDynamic={list.isDynamic}
        isPublic={list.isPublic}
        listLength={userList.totalItems}
        onClick={props.closeRequested}
        onSmartListClick={openSmartListDialog}
        onPublicListClick={openPublicListDialog}
      />
    );
  };

  const renderDrawerContents = () => {
    const sortedLists = _.sortBy(
      lists,
      list => (list.isDynamic ? 1 : -1),
      list => (list.createdAt ? -new Date(list.createdAt) : null),
      list => (list.legacyId ? -list.legacyId : null),
      'id',
    );

    const smartLists = _.filter(sortedLists, list => list.isDynamic);
    const manualLists = _.filter(sortedLists, list => !list.isDynamic);

    function ListItemLink(props: ListItemProps) {
      const { primary, to, selected, icon } = props;

      return (
        <Link href={to}>
          <ListItem button divider selected={selected} onClick={props.onClick}>
            {icon ? (
              <ListItemIcon className={classes.drawerIcon}>{icon}</ListItemIcon>
            ) : null}
            <ListItemText>{primary}</ListItemText>
          </ListItem>
        </Link>
      );
    }

    return (
      <React.Fragment>
        <div className={classes.toolbar} />
        <List className={classes.list}>
          <ListItemLink
            to="/popular"
            primary="Explore"
            icon={<TrendingUp />}
            ListItemProps={{
              selected: router.pathname.toLowerCase() === '/popular',
              button: true,
              divider: true,
            }}
          />
          {isLoggedIn ? (
            <React.Fragment>
              <ListItem
                button
                onClick={handleManualListsClick}
                className={classes.parent}
                selected={manualListOpen}
                divider
              >
                <ListItemIcon>
                  <ListIcon />
                </ListItemIcon>
                <ListItemText primary="My Lists" />
                {manualListOpen ? <ExpandLess /> : <ExpandMore />}
              </ListItem>
              <Collapse in={manualListOpen} timeout="auto" unmountOnExit>
                {_.map(manualLists, renderListItems)}
                <ListItem
                  button
                  onClick={handleModalOpen}
                  className={classes.nested}
                  dense
                  disableGutters
                >
                  <ListItemText
                    style={{
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                    primary="+ Create New List"
                  />
                </ListItem>
              </Collapse>

              <ListItem
                button
                onClick={handleSmartListsClick}
                className={classes.parent}
                selected={smartListOpen}
                divider
              >
                <ListItemIcon>
                  <MovieFilter />
                </ListItemIcon>
                <ListItemText primary="My Smart Lists" />
                {smartListOpen ? <ExpandLess /> : <ExpandMore />}
              </ListItem>
              <Collapse in={smartListOpen} timeout="auto" unmountOnExit>
                {_.map(smartLists, renderListItems)}
              </Collapse>
              <Divider />
            </React.Fragment>
          ) : null}
        </List>
        {isLoggedIn ? (
          <List className={classes.fixedListItems}>
            <ListItem button onClick={handleLogout}>
              <ListItemIcon>
                <PowerSettingsNew className={classes.drawerIcon} />
              </ListItemIcon>
              <ListItemText>Logout</ListItemText>
            </ListItem>
          </List>
        ) : (
          <List>
            <ListItem button onClick={toggleLoginModal}>
              <ListItemIcon>
                <Lock className={classes.drawerIcon} />
              </ListItemIcon>
              <ListItemText>Login</ListItemText>
            </ListItem>
            <ListItem button onClick={toggleSignupModal}>
              <ListItemIcon>
                <PersonAdd className={classes.drawerIcon} />
              </ListItemIcon>
              <ListItemText>Signup</ListItemText>
            </ListItem>
          </List>
        )}
      </React.Fragment>
    );
  };

  const renderDrawer = () => {
    return (
      <SwipeableDrawer
        open={isOpen}
        onOpen={() => setDrawerState(true)}
        onClose={() => setDrawerState(false)}
        anchor="left"
        className={classes.drawer}
        style={{ width: isOpen ? DrawerWidthPx : 0 }}
        disableBackdropTransition={!iOS}
        disableDiscovery={iOS}
        swipeAreaWidth={50}
        ModalProps={{
          onBackdropClick: props.closeRequested,
          onEscapeKeyDown: props.closeRequested,
        }}
        PaperProps={{
          style: { width: DrawerWidthPx },
        }}
      >
        {isLoading() ? <CircularProgress /> : renderDrawerContents()}
      </SwipeableDrawer>
    );
  };

  const isLoading = () => {
    return loadingLists && !listsLoadedOnce;
  };

  return (
    <React.Fragment>
      <div>{renderDrawer()}</div>
      <CreateListDialog open={createDialogOpen} onClose={handleModalClose} />
      <SmartListDialog open={smartListDialogOpen} onClose={handleModalClose} />
      <AuthDialog
        open={authModalOpen}
        onClose={toggleAuthModal}
        initialForm={authModalScreen}
      />
      <PublicListDialog
        open={publicListDialogOpen}
        onClose={handleModalClose}
      />
    </React.Fragment>
  );
}
