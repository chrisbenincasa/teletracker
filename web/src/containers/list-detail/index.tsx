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
// import Breadcrumbs from '@material-ui/lab/Breadcrumbs';
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
import Drawer from '../../components/Drawer';
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

interface DrawerProps {
  drawerOpen: boolean;
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
  WithUserProps &
  DrawerProps;

interface State {
  loadingList: boolean;
}

// const breadcrumbNameMap = {
//   '/lists': 'Lists',
// };

// interface BreadcrumbsProps {
//   lookup: object;
//   onNotFound?: (path: string, isLast: boolean) => string;
// }

// class CustomBreadcrumbs extends Component<BreadcrumbsProps> {
//   render() {
//     return (
//       <Route>
//         {({ location }) => {
//           const pathnames = location.pathname.split('/').filter(x => x);

//           return (
//             <Breadcrumbs arial-label="Breadcrumb">
//               <Link
//                 component={props => <RouterLink {...props} to="/" />}
//                 color="inherit"
//               >
//                 Home
//               </Link>
//               {pathnames.map((_, index) => {
//                 const last = index === pathnames.length - 1;
//                 const to = `/${pathnames.slice(0, index + 1).join('/')}`;

//                 let name: string | undefined = this.props.lookup[to];

//                 if (!name && this.props.onNotFound) {
//                   name = this.props.onNotFound(to, last);
//                 }

//                 return last ? (
//                   <Typography color="textPrimary" key={to}>
//                     {name}
//                   </Typography>
//                 ) : (
//                   <Link
//                     component={props => <RouterLink {...props} to={to} />}
//                     color="inherit"
//                     key={to}
//                   >
//                     {name}
//                   </Link>
//                 );
//               })}
//             </Breadcrumbs>
//           );
//         }}
//       </Route>
//     );
//   }
// }

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
    let { drawerOpen, userSelf } = this.props;

    return (
      <div style={{ display: 'flex' }}>
        <Drawer userSelf={userSelf} open={drawerOpen} />
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  }

  renderListDetail(list: List) {
    let { drawerOpen, listLoading, userSelf } = this.props;

    if (!listLoading && !list) {
      return <Redirect to="/lists" />;
    } else {
      return (
        <div style={{ display: 'flex' }}>
          <Drawer userSelf={userSelf} open={drawerOpen} />
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              flexGrow: 1,
              padding: 20,
            }}
          >
            {/* <CustomBreadcrumbs
              lookup={breadcrumbNameMap}
              onNotFound={() => list.name}
            /> */}
            <Typography component="h1" variant="h4" align="left">
              {list.name}
            </Typography>
            <Grid container spacing={2}>
              {list.things.map(item => (
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
