import {
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  createList,
  ListRetrieveAllPayload,
  retrieveAllLists,
  UserCreateListPayload,
} from '../actions/lists';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { ListsByIdMap } from '../reducers/lists';
import { Loading } from '../reducers/user';
import { List as ListType } from '../types';
import _ from 'lodash';
import ReactGA from 'react-ga';
import RouterLink from '../components/RouterLink';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';

const styles = (theme: Theme) =>
  createStyles({
    root: {
      display: 'flex',
      flexWrap: 'wrap',
      justifyContent: 'flex-start',
      overflow: 'hidden',
    },
    listName: {
      textDecoration: 'none',
      margin: theme.spacing(2, 0),
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: '#000',
      },
    },
    listsContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      padding: theme.spacing(0, 2),
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      flexGrow: 1,
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  isCheckingAuth: boolean;
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps &
  WithRouterProps;

interface State {
  createDialogOpen: boolean;
  listName: string;
}

class Lists extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
    listName: '',
  };

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.props.ListRetrieveAllInitiated();

    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }

  renderLoading() {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  renderItemPreviews = (list: ListType) => {
    if (!list.items) {
      return null;
    }

    let things = list.items.slice(0, 6);
    let { classes, userSelf } = this.props;

    return (
      <div className={classes.root}>
        <Grid container spacing={2} direction="row" wrap="nowrap" item={true}>
          {things.map(item => (
            <ItemCard
              key={item.id}
              userSelf={userSelf}
              item={item}
              listContext={list}
              withActionButton
            />
          ))}
        </Grid>
      </div>
    );
  };

  renderList = (userList: ListType) => {
    let { classes, listsById } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;

    return (
      <div className={classes.listContainer} key={userList.id}>
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
        {this.renderItemPreviews(list)}
      </div>
    );
  };

  renderLists() {
    let { classes, loadingLists, retrievingUser, userSelf } = this.props;
    if (retrievingUser || !userSelf || loadingLists) {
      return this.renderLoading();
    } else {
      return (
        <div className={classes.listsContainer}>
          {_.map(this.props.listsById, this.renderList)}
        </div>
      );
    }
  }

  render() {
    let { isAuthed, router } = this.props;
    if (!isAuthed) {
      router.replace('/login');
    }

    return (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderLists()}</div>
    );
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

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      ListRetrieveAllInitiated: retrieveAllLists,
      createList,
    },
    dispatch,
  );

export default withRouter(
  withUser(
    withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(Lists)),
  ),
);
