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
import {
  Link as RouterLink,
  Redirect,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
} from '../../actions/lists';
import ItemCard from '../../components/ItemCard';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { ListsByIdMap } from '../../reducers/lists';
import { layoutStyles } from '../../styles';
import { List } from '../../types';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    listName: {
      textDecoration: 'none',
      margin: `${theme.spacing(2)}px 0`,
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: '#000',
      },
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1 0 auto',
      padding: `0 ${theme.spacing(2)}px`,
    },
  });

interface OwnProps {
  isAuthed?: boolean;
  listLoading: boolean;
  listsById: ListsByIdMap;
}

interface DispatchProps {
  retrieveList: (payload: ListRetrieveInitiatedPayload) => void;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  loadingList: boolean;
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      loadingList: true,
    };
  }

  componentDidMount() {
    console.log(this.props);

    this.props.retrieveList({
      listId: this.props.match.params.id,
      force: false,
    });
  }

  componentDidUpdate(oldProps: Props) {
    if (!this.props.listLoading && oldProps.listLoading) {
      this.setState({ loadingList: false });
    } else if (this.props.match.params.id !== oldProps.match.params.id) {
      this.props.retrieveList({
        listId: this.props.match.params.id,
        force: false,
      });
    }
  }

  renderLoading() {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  }

  renderListDetail(list: List) {
    let { classes, listLoading, userSelf } = this.props;

    if (!listLoading && !list) {
      return <Redirect to="/lists" />;
    } else {
      return (
        <div className={classes.root}>
          <div className={classes.listContainer}>
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
            <Grid container spacing={2}>
              {list.things.map(item => (
                <ItemCard
                  key={item.id}
                  userSelf={userSelf}
                  item={item}
                  listContext={list}
                  itemCardVisible={false}
                  withActionButton
                />
              ))}
            </Grid>
          </div>
        </div>
      );
    }
  }

  render() {
    let { listsById, match, userSelf } = this.props;
    let list = listsById[Number(match.params.id)];

    return !list || !userSelf
      ? this.renderLoading()
      : this.renderListDetail(list);
  }
}

const mapStateToProps: (appState: AppState) => OwnProps = appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    listLoading: appState.lists.operation.inProgress,
    listsById: appState.lists.listsById,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      retrieveList: ListRetrieveInitiated,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(ListDetail),
    ),
  ),
);
