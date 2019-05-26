import {
  Badge,
  createStyles,
  CssBaseline,
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
import { Link as RouterLink, Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import {
  ListRetrieveAllInitiated,
  ListRetrieveAllPayload,
} from '../../actions/lists';
import { createList, UserCreateListPayload } from '../../actions/user';
import withUser, { WithUserProps } from '../../components/withUser';
import Drawer from '../../components/Drawer';
import { AppState } from '../../reducers';
import { List as ListType, Thing } from '../../types';
import { ListsByIdMap } from '../../reducers/lists';
import { Loading } from '../../reducers/user';
import { layoutStyles } from '../../styles';
import ItemCard from '../../components/ItemCard';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    root: {
      display: 'flex',
      flexWrap: 'wrap',
      justifyContent: 'flex-start',
      overflow: 'hidden',
    },
    listName: {
      textDecoration: 'none',
      marginBottom: theme.spacing(2),
    },
    listsContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      margin: `${theme.spacing(2)}px 0`,
      width: '100%',
    },
    margin: {
      margin: theme.spacing(2),
      marginRight: theme.spacing(3),
    },
    listItemCount: {
      margin: theme.spacing(2),
      marginLeft: 0,
      paddingRight: theme.spacing(2),
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      flexGrow: 1,
      paddingLeft: theme.spacing(2),
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  isCheckingAuth: boolean;
  listsById: ListsByIdMap;
  loadingLists: boolean;
  loading: Partial<Loading>;
  drawerOpen: boolean;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
  createList: (payload?: UserCreateListPayload) => void;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

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
    this.props.ListRetrieveAllInitiated();
  }

  renderLoading() {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  renderItemPreviews = (list: ListType) => {
    let things: (Thing)[] = list.things.slice(0, 4);
    let { classes, userSelf } = this.props;

    return (
      <div className={classes.root}>
        <Grid
          container
          spacing={2}
          direction="row"
          lg={8}
          md={4}
          sm={2}
          wrap="nowrap"
          item={true}
        >
          {things.map(item => (
            <ItemCard
              key={item.id}
              userSelf={userSelf}
              item={item}
              itemCardVisible={false}
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
      <div className={classes.listsContainer} key={userList.id}>
        <Typography
          component={props => (
            <RouterLink {...props} to={'/lists/' + list.id} />
          )}
          variant="h5"
          className={classes.listName}
        >
          <Badge
            className={classes.listItemCount}
            badgeContent={list.things.length}
            color="primary"
          >
            {list.name}
          </Badge>
        </Typography>
        {this.renderItemPreviews(list)}
      </div>
    );
  };

  renderLists() {
    let {
      classes,
      drawerOpen,
      loadingLists,
      retrievingUser,
      userSelf,
    } = this.props;
    if (retrievingUser || !userSelf || loadingLists) {
      return this.renderLoading();
    } else {
      return (
        <div className={classes.listContainer}>
          <CssBaseline />
          {userSelf.lists.map(this.renderList)}
        </div>
      );
    }
  }

  render() {
    let { isAuthed, userSelf, drawerOpen } = this.props;
    return isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>
        <Drawer userSelf={userSelf} open={drawerOpen} />
        {this.renderLists()}
      </div>
    ) : (
      <Redirect to="/login" />
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
    )(Lists),
  ),
);
