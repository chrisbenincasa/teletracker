import {
  createStyles,
  CssBaseline,
  LinearProgress,
  Theme,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
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
import Drawer from '../components/Drawer';
import Toolbar from '../components/Toolbar/Toolbar';
import Footer from '../components/Footer';
import Logout from './Logout';
import Explore from './Explore';
import NoMatch404 from './NoMatch404';
import { GA_TRACKING_ID } from '../constants/';
import ReactGA from 'react-ga';

const styles = (theme: Theme) =>
  createStyles({
    mainContent: {
      transition: theme.transitions.create('margin', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
      }),
      paddingBottom: '10rem',
      [theme.breakpoints.down('sm')]: {
        paddingBottom: '2rem',
      },
    },
    root: {
      flexGrow: 1,
      minHeight: '100vh',
      position: 'relative',
      display: 'flex',
      flexDirection: 'column',
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
  searchVisible: boolean;
}

class App extends Component<Props, State> {
  state = {
    drawerOpen: false,
    searchVisible: false,
  };

  componentDidMount() {
    ReactGA.initialize(GA_TRACKING_ID);
  }

  componentDidUpdate(prevProps: Props) {
    // If user navigates on mobile with drawer open, let's close it
    if (
      this.props.location.pathname !== prevProps.location.pathname ||
      this.props.location.search !== prevProps.location.search
    ) {
      if (['xs', 'sm', 'md'].includes(this.props.width)) {
        this.toggleDrawer(true);
      }
    }
  }

  setSearchVisibility = isVisible => {
    // If search is visible, we want to not show toolbar search
    if (!isVisible !== this.state.searchVisible) {
      this.setState({ searchVisible: !isVisible });
    }
  };

  toggleDrawer = (close?: boolean) => {
    // If close is provided, close the drawer, otherwise flip it
    if (close) {
      this.setState({ drawerOpen: false });
    } else {
      this.setState({ drawerOpen: !this.state.drawerOpen });
    }
  };

  render() {
    let { classes, isAuthed, isBooting, location } = this.props;

    return (
      <div className={classes.root}>
        <CssBaseline />
        <Toolbar
          drawerOpen={this.state.drawerOpen}
          onDrawerChange={close => this.toggleDrawer(close)}
          searchVisible={this.state.searchVisible}
        />
        {!isBooting ? (
          <React.Fragment>
            <div style={{ flexGrow: 1 }}>
              <Drawer
                open={this.state.drawerOpen}
                closeRequested={() => this.toggleDrawer()}
              />
              <main
                style={{
                  display: 'flex',
                  flexDirection: isAuthed ? 'row' : 'column',
                }}
                className={classes.mainContent}
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
                    render={props => (
                      <Search
                        {...props}
                        inViewportChange={isVisible =>
                          this.setSearchVisibility(isVisible)
                        }
                      />
                    )}
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
                  <Route
                    exact
                    path="/new"
                    render={props => <New {...props} />}
                  />
                  <Route exact path="/popular" component={Popular} />
                  <Route
                    exact
                    path="/movies"
                    render={props => <Explore initialType="movie" {...props} />}
                  />
                  <Route
                    exact
                    path="/shows"
                    render={props => <Explore initialType="shows" {...props} />}
                  />
                  <Route
                    exact
                    path="/all"
                    render={props => <Explore {...props} />}
                  />
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
                  <Route component={NoMatch404} />
                </Switch>
              </main>
            </div>
            {location.pathname.toLowerCase() === '/popular' ||
            (isAuthed && location.pathname === '/') ? null : (
              <Footer />
            )}
          </React.Fragment>
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
