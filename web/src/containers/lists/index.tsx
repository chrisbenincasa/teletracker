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
    let things: (Thing )[] = list.things.slice(0, 4);
    let { classes, userSelf } = this.props;

    return (
      <div className={classes.root}>
        <Grid container spacing={16} direction='row' lg={8} wrap='nowrap'>
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
    let { listsById, classes } = this.props;
    let listWithDetails = listsById[userList.id];
    let list = listWithDetails || userList;

    return (
      <div className={classes.listsContainer}>
        <Typography
          component={props => <RouterLink {...props} to={'/lists/' + list.id} />}
          variant="h5"
          className={classes.listName}
          >
          <Badge
            className={classes.margin}
            badgeContent={list.things.length}
            color="primary"
            style={{marginLeft: 0, paddingRight: 15}}
          >
            {list.name}
          </Badge>
        </Typography>
        {this.renderItemPreviews(list)}
      </div>
    );
  };

  renderLists() {
    let { userSelf } = this.props;
    if (
      this.props.retrievingUser ||
      !userSelf ||
      this.props.loadingLists
    ) {
      return this.renderLoading();
    } else {
      return (
        <div style={{display: 'flex'}}>
          <Drawer />
          <div style={{display: 'flex', flexDirection: 'column', flexGrow: 1, paddingLeft: 20}}>
            <CssBaseline />
            {userSelf.lists.map(this.renderList)}
          </div>
        </div>
      );
    }
  }

  render() {
    return this.props.isAuthed ? this.renderLists() : <Redirect to="/login" />;
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
