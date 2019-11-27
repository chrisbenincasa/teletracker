import {
  createStyles,
  Paper,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { login, LoginSuccessful, logInWithGoogle } from '../actions/auth';
import { AppState } from '../reducers';
import { Redirect } from 'react-router';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';
import LoginForm from '../components/Auth/LoginForm';

const styles = (theme: Theme) =>
  createStyles({
    main: {
      width: 'auto',
      display: 'block', // Fix IE 11 issue.
      marginLeft: theme.spacing(3),
      marginRight: theme.spacing(3),
      [theme.breakpoints.up(400 + theme.spacing(6))]: {
        width: 400,
        marginLeft: 'auto',
        marginRight: 'auto',
      },
    },
    paper: {
      marginTop: theme.spacing(8),
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: theme.spacing(2, 3, 3),
      position: 'relative',
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  login: (email: string, password: string) => void;
  logInWithGoogle: () => any;
  isLoggingIn: boolean;
  logInSuccessful: (token: string) => any;
  changePage: () => void;
  redirect_uri?: string;
}

interface State {
  email: string;
  password: string;
}

class Login extends Component<Props, State> {
  state: State = {
    email: '',
    password: '',
  };

  componentDidMount(): void {
    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
  }

  render() {
    let { isAuthed, classes } = this.props;

    return !isAuthed ? (
      <div className={classes.main}>
        <Paper className={classes.paper}>
          <LoginForm />
        </Paper>
      </div>
    ) : (
      <Redirect to="/" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isLoggingIn: appState.auth.isLoggingIn,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      login: (email: string, password: string) => login(email, password),
      changePage: () => push('/'),
      logInWithGoogle,
      logInSuccessful: (token: string) => LoginSuccessful(token),
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(mapStateToProps, mapDispatchToProps)(Login),
);
