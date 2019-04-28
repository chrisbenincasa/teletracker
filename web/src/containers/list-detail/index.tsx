import {
  createStyles,
  Grid,
  LinearProgress,
  Link,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import Breadcrumbs from '@material-ui/lab/Breadcrumbs';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  Redirect,
  Route,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import {
  ListRetrieveInitiatedPayload,
  ListRetrieveInitiated,
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

const breadcrumbNameMap = {
  '/lists': 'Lists',
};

interface BreadcrumbsProps {
  lookup: object;
  onNotFound?: (path: string, isLast: boolean) => string;
}

class CustomBreadcrumbs extends Component<BreadcrumbsProps> {
  render() {
    return (
      <Route>
        {({ location }) => {
          const pathnames = location.pathname.split('/').filter(x => x);

          return (
            <Breadcrumbs arial-label="Breadcrumb">
              <Link
                component={props => <RouterLink {...props} to="/" />}
                color="inherit"
              >
                Home
              </Link>
              {pathnames.map((_, index) => {
                const last = index === pathnames.length - 1;
                const to = `/${pathnames.slice(0, index + 1).join('/')}`;

                let name: string | undefined = this.props.lookup[to];

                if (!name && this.props.onNotFound) {
                  name = this.props.onNotFound(to, last);
                }

                return last ? (
                  <Typography color="textPrimary" key={to}>
                    {name}
                  </Typography>
                ) : (
                  <Link
                    component={props => <RouterLink {...props} to={to} />}
                    color="inherit"
                    key={to}
                  >
                    {name}
                  </Link>
                );
              })}
            </Breadcrumbs>
          );
        }}
      </Route>
    );
  }
}

class ListDetail extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      loadingList: true,
    };
  }

  componentDidMount() {
    this.props.retrieveList({
      listId: this.props.match.params.id,
      force: false,
    });
  }

  componentDidUpdate(oldProps: Props) {
    if (!this.props.listLoading && oldProps.listLoading) {
      this.setState({ loadingList: false });
    }
  }

  renderLoading() {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  renderListDetail(list: List) {
    let { classes, userSelf, listLoading } = this.props;

    if (!listLoading && !list) {
      return <Redirect to="/lists" />;
    } else {
      return (
        <div className={classes.layout}>
          <CustomBreadcrumbs
            lookup={breadcrumbNameMap}
            onNotFound={() => list!.name}
          />
          <Typography component="h1" variant="h4" align="left">
            {list!.name}
          </Typography>
          <Grid container spacing={16}>
            {list!.things.map(item => (
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
    }
  }

  render() {
    let { userSelf } = this.props;
    let list = this.props.listsById[Number(this.props.match.params.id)];

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
