import {
  Badge,
  Button,
  createStyles,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  LinearProgress,
  TextField,
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
import DrawerUI from '../../components/Drawer';
import { USER_SELF_CREATE_LIST } from '../../constants/user';
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
    drawer: {
      width: 240,
      flexShrink: 0,
    },
    drawerPaper: {
      width: 240,
    },
    toolbar: theme.mixins.toolbar,
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
      <React.Fragment>
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
      </React.Fragment>
    );
  };



  renderLists() {
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
        <div style={{display: 'flex', flexGrow: 1, paddingLeft: 20}}>
          <CssBaseline />
          <DrawerUI/>
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
