import {
  createStyles,
  CssBaseline,
  Theme,
  withWidth,
  WithStyles,
  withStyles,
  LinearProgress,
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
import Home from './Home';
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
import Footer from '../components/Footer';
import Logout from './Logout';
import _ from 'lodash';

const styles = (theme: Theme) =>
  createStyles({
    mainContent: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
      }),
      marginLeft: 0,
      paddingBottom: '9rem',
      [theme.breakpoints.down('sm')]: {
        paddingBottom: '2rem',
      },
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
      minHeight: '100vh',
      position: 'relative',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
  isBooting: boolean;
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

  componentDidUpdate(prevProps: Props) {
    // If user navigates on mobile with drawer open, let's close it
    if (
      this.props.location.pathname !== prevProps.location.pathname ||
      this.props.location.search !== prevProps.location.search
    ) {
      if (['xs', 'sm', 'md'].includes(this.props.width)) {
        this.closeDrawer();
      }
    }
  }

  toggleDrawer = () => {
    this.setState({ drawerOpen: !this.state.drawerOpen });
  };

  closeDrawer = () => {
    this.setState({ drawerOpen: false });
  };

  render() {
    let { classes, isAuthed, isBooting, location } = this.props;

    return (
      <div className={classes.root}>
        <CssBaseline />
        <Toolbar
          drawerOpen={this.state.drawerOpen}
          onDrawerChange={() => this.toggleDrawer()}
        />
        {!isBooting ? (
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
                <Route
                  exact
                  path="/"
                  render={props =>
                    isAuthed ? <Popular {...props} /> : <Home {...props} />
                  }
                />
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
                {/* This is here just to allow for easy testing without logging in/out.  Need to remove at some point */}
                <Route exact path="/home" component={Home} />
                <Route exact path="/login" component={Login} />
                <Route exact path="/signup" component={Signup} />
                <Route exact path="/new" render={props => <New {...props} />} />
                <Route exact path="/popular" component={Popular} />
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
            {location.pathname.toLowerCase() === '/popular' ||
            (isAuthed && location.pathname === '/') ? null : (
              <Footer />
            )}
          </div>
        ) : (
          <LinearProgress />
        )}
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isBooting: appState.startup.isBooting,
  };
};

export default withWidth()(
  withRouter(withStyles(styles)(connect(mapStateToProps)(App))),
);
