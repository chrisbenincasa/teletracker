import {
  AppBar,
  Button,
  createStyles,
  Toolbar,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link, Route } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { checkAuth } from '../../actions/auth';
import LogoutButton from '../../components/LogoutButton';
import { AppState } from '../../reducers';
import About from '../about';
import Home from '../home';
import Login from '../login';

const styles = createStyles({
  root: {
    flexGrow: 1,
  },
  grow: {
    flexGrow: 1,
  },
  menuButton: {
    marginLeft: -12,
    marginRight: 20,
  },
});

interface OwnProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
}

interface DispatchProps {
  checkAuth: () => void;
}

type Props = DispatchProps & OwnProps;

class App extends Component<Props> {
  componentDidMount() {
    this.props.checkAuth();
  }

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ anchorEl: null });
  };

  render() {
    let { classes } = this.props;

    return (
      <React.Fragment>
        <AppBar position="static">
          <Toolbar>
            <Typography variant="h6" color="inherit" className={classes.grow}>
              Teletracker
            </Typography>
            <Button
              component={props => <Link {...props} to="/" />}
              color="inherit"
            >
              Home
            </Button>
            {!this.props.isAuthed ? (
              <Button
                component={props => <Link {...props} to="/login" />}
                color="inherit"
              >
                Login
              </Button>
            ) : null}
            {this.props.isAuthed ? <LogoutButton /> : null}
          </Toolbar>
        </AppBar>
        <div>
          <main>
            <Route exact path="/" component={Home} />
            <Route exact path="/about-us" component={About} />
            <Route exact path="/login" component={Login} />
            <Route exact path="/logout" component={About} />
          </main>
        </div>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  console.log(appState);
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      checkAuth,
    },
    dispatch,
  );
};

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(App),
);
