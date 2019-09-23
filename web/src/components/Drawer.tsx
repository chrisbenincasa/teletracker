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
} from '@material-ui/core';
import { AddCircle, PowerSettingsNew, Settings } from '@material-ui/icons';
import classNames from 'classnames';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../actions/auth';
import {
  LIST_RETRIEVE_ALL_INITIATED,
  ListRetrieveAllPayload,
  retrieveAllLists,
} from '../actions/lists';
import withUser, { WithUserProps } from '../components/withUser';
import CreateListDialog from '../components/CreateListDialog';
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { layoutStyles } from '../styles';
import { List as ListType } from '../types';
import RouterLink from './RouterLink';

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
  logout: () => void;
}

interface State {
  createDialogOpen: boolean;
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
  to: string;
  primary?: string;
  selected?: boolean;
}

// TODO: Get type definitions for props working
const ListItemLink = withStyles(styles, { withTheme: true })(
  (props: LinkProps & WithStyles<typeof styles, true>) => {
    const { index, primary, selected, listLength } = props;

    const backgroundColor =
      props.theme.palette.primary[
        index ? (index < 9 ? `${9 - index}00` : 100) : 900
      ];

    return (
      <ListItem button to={props.to} component={RouterLink} selected={selected}>
        <ListItemAvatar>
          <Avatar
            style={{
              backgroundColor,
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
  },
);

class Drawer extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    loadingLists: true,
  };

  componentDidMount() {
    if (!Boolean(this.props.loading)) {
    }
    this.props.retrieveAllLists({ includeThings: false });
  }

  componentDidUpdate(oldProps: Props) {
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
    });
  };

  renderListItems = (userList: ListType, index: number) => {
    let { listsById, match } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;
    const listPath = `/lists/${list.id}`;

    return (
      <ListItemLink
        index={index}
        key={userList.id}
        to={`/lists/${list.id}`}
        // TODO: Improve logic for selection
        selected={listPath === location.pathname}
        primary={list.name}
        listLength={userList.thingCount}
      />
    );
  };

  renderDrawerContents() {
    let { classes, listsById } = this.props;
    function ListItemLink(props: ListItemProps) {
      const { primary, to, selected } = props;

      return (
        <ListItem button component={RouterLink} to={to} selected={selected}>
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
    let { classes, open } = this.props;

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

  render() {
    return (
      <React.Fragment>
        {this.renderDrawer()}
        <CreateListDialog
          open={this.state.createDialogOpen}
          onClose={this.handleModalClose.bind(this)}
        />
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
      logout,
      retrieveAllLists,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles, { withTheme: true })(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Drawer),
    ),
  ),
  () => null,
);
