import {
  createStyles,
  CssBaseline,
  Theme,
  withWidth,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import clsx from 'clsx';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Route,
  RouteComponentProps,
  Switch,
  withRouter,
} from 'react-router-dom';
import { AppState } from '../reducers';
import Search from './Search';
import Account from './Account';
import ListDetail from './ListDetail';
import ItemDetail from './ItemDetail';
import Lists from './Lists';
import Login from './Login';
import Signup from './Signup';
import New from './New';
import Popular from './Popular';
import PersonDetail from './PersonDetail';
import Drawer, { DrawerWidthPx } from '../components/Drawer';
import Toolbar from '../components/Toolbar';
import Logout from './Logout';
import Genre from './Genre';
import _ from 'lodash';

const styles = (theme: Theme) =>
  createStyles({
    mainContent: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
      }),
      marginLeft: 0,
    },
    mainContentShift: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.easeOut,
        duration: theme.transitions.duration.enteringScreen,
      }),
      [theme.breakpoints.up('sm')]: {
        marginLeft: DrawerWidthPx,
      },
    },
    root: {
      flexGrow: 1,
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
}

interface WidthProps {
  width: string;
}

type Props = OwnProps & RouteComponentProps & WidthProps;

interface State {
  drawerOpen: boolean;
}

class App extends Component<Props, State> {
  state = {
    drawerOpen: false,
  };

  toggleDrawer = () => {
    this.setState({ drawerOpen: !this.state.drawerOpen });
  };

  render() {
    let { classes, isAuthed } = this.props;

    return (
      <div className={classes.root}>
        <CssBaseline />
        <Toolbar drawerOpen={this.toggleDrawer.bind(this)} />
        <div>
          {/* TODO: investigate better solution for flexDirection issue as it relates to the LinearProgress bar display */}
          <Drawer open={this.state.drawerOpen} />
          <main
            style={{
              display: 'flex',
              flexDirection: isAuthed ? 'row' : 'column',
            }}
            className={clsx(classes.mainContent, {
              [classes.mainContentShift]: this.state.drawerOpen,
            })}
          >
            <Switch>
              <Route exact path="/" render={props => <Popular {...props} />} />
              <Route
                exact
                path="/search"
                render={props => <Search {...props} />}
              />
              <Route
                exact
                path="/account"
                render={props => <Account {...props} />}
              />
              <Route exact path="/login" component={Login} />
              <Route exact path="/signup" component={Signup} />
              <Route exact path="/new" render={props => <New {...props} />} />
              <Route exact path="/popular" component={Popular} />
              <Route exact path="/genres/:id" component={Genre} />
              <Route exact path="/logout" component={Logout} />
              <Route
                exact
                path="/lists"
                render={props => <Lists {...props} />}
              />
              <Route
                exact
                path="/lists/:id"
                render={props => <ListDetail {...props} />}
              />
              <Route exact path="/person/:id" component={PersonDetail} />
              <Route
                exact
                path="/:type/:id"
                render={props => <ItemDetail {...props} />}
              />
            </Switch>
          </main>
        </div>
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
  };
};

export default withWidth()(
  withRouter(withStyles(styles)(connect(mapStateToProps)(App))),
);
